/*
 * Copyright 2011-2018 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.decodified.scalassh

import java.util.concurrent.TimeUnit
import java.io.{FileInputStream, FileNotFoundException, InputStream}

import com.jcraft.jsch.agentproxy.{AgentProxy, Connector, ConnectorFactory}
import com.jcraft.jsch.agentproxy.sshj.AuthAgent
import org.slf4j.LoggerFactory
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.method.AuthMethod

import scala.io.Source
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

final class SshClient(val config: HostConfig) extends ScpTransferable {
  lazy val log                 = LoggerFactory.getLogger(getClass)
  lazy val endpoint            = config.hostName + ':' + config.port
  lazy val authenticatedClient = connect(client).flatMap(authenticate)
  val client                   = createClient(config)

  def exec(command: Command): Try[CommandResult] =
    authenticatedClient.flatMap { client =>
      startSession(client).flatMap { session =>
        execWithSession(command, session)
      }
    }

  def execPTY(command: Command): Try[CommandResult] =
    authenticatedClient.flatMap { client =>
      startSession(client).flatMap { session =>
        config.ptyConfig.fold(session.allocateDefaultPTY()) { ptyConf =>
          session.allocatePTY(ptyConf.term, ptyConf.cols, ptyConf.rows, ptyConf.width, ptyConf.height, ptyConf.modes)
        }
        execWithSession(command, session)
      }
    }

  def execWithSession(command: Command, session: Session): Try[CommandResult] = {
    log.info("Executing SSH command on {}: \"{}\"", Seq(endpoint, command.command): _*)
    protect("Could not execute SSH command on") {
      val channel = session.exec(command.command)
      command.input.inputStream.foreach(new StreamCopier().copy(_, channel.getOutputStream))
      command.timeout orElse config.commandTimeout match {
        case Some(timeout) => channel.join(timeout.toLong, TimeUnit.MILLISECONDS)
        case None          => channel.join()
      }
      new CommandResult(channel)
    }
  }

  protected def createClient(config: HostConfig): SSHClient = {
    val client = new SSHClient(config.sshjConfig)
    config.connectTimeout.foreach(client.setConnectTimeout)
    config.connectionTimeout.foreach(client.setTimeout)
    client.addHostKeyVerifier(config.hostKeyVerifier)
    if (config.enableCompression) client.useCompression()
    client
  }

  protected def connect(client: SSHClient): Try[SSHClient] = {
    require(!client.isConnected)
    protect("Could not connect to") {
      log.info("Connecting to {} ...", endpoint: Any)
      client.connect(config.hostName, config.port)
      client
    }
  }

  protected def authenticate(client: SSHClient): Try[SSHClient] = {
    def keyProviders(locations: List[String], passProducer: PasswordProducer): List[KeyProvider] = {
      def inputStream(location: String): Option[InputStream] = {
        if (location.startsWith("classpath:")) {
          val resource = location.substring("classpath:".length)
          Option(getClass.getClassLoader.getResourceAsStream(resource))
            .orElse(
              throw new RuntimeException(
                "Classpath resource '" + resource + "' containing private key could not be found"))
        } else {
          try Some(new FileInputStream(location))
          catch { case _: FileNotFoundException => None }
        }
      }
      locations.flatMap { location =>
        inputStream(location).map { stream =>
          val privateKey = Source.fromInputStream(stream).getLines().mkString("\n")
          client.loadKeys(privateKey, null, passProducer)
        }
      } match {
        case Nil => sys.error("None of the configured keyfiles exists: " + locations.mkString(", "))
        case x   => x
      }
    }

    def agentProxyAuthMethods: Seq[AuthMethod] = {
      def authMethods(agent: AgentProxy): Seq[AuthMethod] = agent.getIdentities.map(new AuthAgent(agent, _))
      val agentConnector: Try[Connector]                  = Try { ConnectorFactory.getDefault.createConnector() }
      val agentProxy: Try[AgentProxy]                     = agentConnector map (new AgentProxy(_))
      agentProxy map authMethods match {
        case Success(m) => m
        case Failure(e) => throw new RuntimeException("Agent proxy could not be initialized", e)
      }
    }

    require(client.isConnected && !client.isAuthenticated)
    log.info("Authenticating to {} using {} ...", Seq(endpoint, config.login.user): _*)
    config.login match {
      case PasswordLogin(user, passProducer) =>
        protect("Could not authenticate (with password) to") {
          client.authPassword(user, passProducer)
          client
        }
      case PublicKeyLogin(user, passProducer, keyfileLocations) =>
        protect("Could not authenticate (with keyfile) to") {
          client.authPublickey(user, keyProviders(keyfileLocations, passProducer.orNull): _*)
          client
        }
      case AgentLogin(user, _) =>
        protect("Could not authenticate (with agent proxy) to") {
          client.auth(user, agentProxyAuthMethods: _*)
          client
        }
    }
  }

  protected def startSession(client: SSHClient): Try[Session] = {
    require(client.isConnected && client.isAuthenticated)
    protect("Could not start SSH session on") {
      client.startSession()
    }
  }

  def close(): Unit = {
    log.info("Closing connection to {} ...", endpoint: Any)
    client.close()
  }

  protected def protect[T](errorMsg: => String)(f: => T): Try[T] =
    try Success(f)
    catch { case NonFatal(e) => Failure(SSH.Error(errorMsg + " " + endpoint, e)) }
}

object SshClient {
  def apply(host: String, configProvider: HostConfigProvider = HostFileConfig()): Try[SshClient] =
    configProvider(host).map(new SshClient(_))
}
