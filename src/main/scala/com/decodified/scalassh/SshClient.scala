package com.decodified.scalassh

import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient
import java.io.File
import org.slf4j.LoggerFactory

class SshClient(val config: HostConfig) {
  lazy val log = LoggerFactory.getLogger(getClass)
  lazy val endpoint = config.hostName + ':' + config.port
  lazy val authenticatedClient = connect(client).right.flatMap(authenticate)
  val client = createClient(config)

  def exec(command: Command): Validated[CommandResult] = {
    authenticatedClient.right.flatMap { client =>
      startSession(client).right.flatMap { session =>
        log.info("Executing SSH command on {}: \"{}\"", endpoint, command.command)
        protect("Could not execute SSH command on") {
          val channel = session.exec(command.command)
          command.input.inputStream.foreach(new StreamCopier().copy(_, channel.getOutputStream))
          (command.timeout orElse config.commandTimeout) match {
            case Some(timeout) => channel.join(timeout, TimeUnit.MILLISECONDS)
            case None => channel.join()
          }
          new CommandResult(channel)
        }
      }
    }
  }

  protected def createClient(config: HostConfig) = {
    make(new SSHClient(config.sshjConfig)) { client =>
      config.connectTimeout.foreach(client.setConnectTimeout(_))
      config.connectionTimeout.foreach(client.setTimeout(_))
      client.addHostKeyVerifier(config.hostKeyVerifier)
      if (config.enableCompression) client.useCompression()
    }
  }

  protected def connect(client: SSHClient) = {
    require(!client.isConnected)
    protect("Could not connect to") {
      log.info("Connecting to {} ...", endpoint)
      client.connect(config.hostName, config.port)
      client
    }
  }

  protected def authenticate(client: SSHClient) = {
    require(client.isConnected && !client.isAuthenticated)
    log.info("Authenticating to {} using {} ...", endpoint, config.login)
    config.login match {
      case PasswordLogin(user, passProducer) =>
        protect("Could not authenticate (with password) to") {
          client.authPassword(user, passProducer)
          client
        }
      case PublicKeyLogin(user, None, keyfileLocations) =>
        protect("Could not authenticate (with keyfile) to") {
          client.authPublickey(user, keyfileLocations.filter(new File(_).exists): _*)
          client
        }
      case PublicKeyLogin(user, Some(passProducer), keyfileLocations) =>
        protect("Could not authenticate (with encrypted keyfile) to") {
          client.authPublickey(user, keyfileLocations.map(loc => client.loadKeys(loc, passProducer)): _*)
          client
        }
    }
  }

  protected def startSession(client: SSHClient) = {
    require(client.isConnected && client.isAuthenticated)
    protect("Could not start SSH session on") {
      client.startSession()
    }
  }

  def close() {
    log.info("Closing connection to {} ...", endpoint)
    client.close()
  }

  protected def protect[T](errorMsg: => String)(f: => T) = {
    try { Right(f) }
    catch { case e: Exception => Left("%s %s due to %s".format(errorMsg, endpoint, e)) }
  }
}

object SshClient {
  def apply(host: String, configProvider: HostConfigProvider = HostFileConfig()): Validated[SshClient] = {
    configProvider(host).right.map(new SshClient(_))
  }
}