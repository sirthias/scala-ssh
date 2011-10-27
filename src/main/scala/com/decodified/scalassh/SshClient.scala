package com.decodified.scalassh

import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient
import java.io.File

class SshClient(val endpoint: SshEndpoint, val login: SshLogin = HostFileLogin, val config: SshConfig = SshConfig()) {
  val unconnectedClient = createClient
  lazy val client = connect(unconnectedClient).right.flatMap(authenticate(login, _))

  def exec(command: Command): Either[String, CommandResult] = {
    client.right.flatMap { client =>
      startSession(client).right.flatMap { session =>
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

  protected def createClient = make(new SSHClient(config.sshjConfig)) { client =>
    config.connectTimeout.foreach(client.setConnectTimeout(_))
    config.connectionTimeout.foreach(client.setTimeout(_))
    client.addHostKeyVerifier(endpoint.hostKeyVerifier)
    if (config.useCompression) client.useCompression()
  }

  protected def connect(client: SSHClient) = {
    protect("Could not connect to") {
      client.connect(endpoint.address, endpoint.port)
      client
    }
  }

  protected def authenticate(login: SshLogin, client: SSHClient): Either[String, SSHClient] = {
    login match {
      case HostFileLogin =>
        val hostFile = new File(config.hostFilesDir + File.separator + endpoint.address.getHostName)
        HostFileLogin.loginFor(hostFile).right.flatMap(authenticate(_, client))
      case PasswordLogin(user, passProducer) =>
        protect("Could not authenticate (with password) to") {
          client.authPassword(user, passProducer)
          client
        }
      case PublicKeyLogin(user, None, keyfileLocations) =>
        protect("Could not authenticate (with keyfile) to") {
          client.authPublickey(user, keyfileLocations: _*)
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
    protect("Could not start SSH session on") {
      client.startSession()
    }
  }

  def close() {
    client.right.foreach(_.close())
  }

  protected def protect[T](errorMsg: => String)(f: => T) = {
    try { Right(f) } catch {
      case e: Exception => Left("%s %s:%s due to %s".format(errorMsg, endpoint.address, endpoint.port, e))
    }
  }
}