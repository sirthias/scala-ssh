package com.decodified.scalassh

import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient

class SshClient(val host: String,
                val configProvider: HostConfigProvider = new HostFileConfig(HostFileConfig.DefaultHostFileDir)) {
  val config = configProvider(host)
  val unconnectedClient = config.right.map(createClient)
  lazy val client = unconnectedClient.right.flatMap(connect).right.flatMap(authenticate)

  def exec(command: Command): Either[String, CommandResult] = {
    client.right.flatMap { client =>
      startSession(client).right.flatMap { session =>
        protect("Could not execute SSH command on") {
          val channel = session.exec(command.command)
          command.input.inputStream.foreach(new StreamCopier().copy(_, channel.getOutputStream))
          (command.timeout orElse rightConfig.commandTimeout) match {
            case Some(timeout) => channel.join(timeout, TimeUnit.MILLISECONDS)
            case None => channel.join()
          }
          new CommandResult(channel)
        }
      }
    }
  }

  protected def rightConfig = config.right.get

  protected def createClient(config: HostConfig) = {
    make(new SSHClient(config.sshjConfig)) { client =>
      config.connectTimeout.foreach(client.setConnectTimeout(_))
      config.connectionTimeout.foreach(client.setTimeout(_))
      client.addHostKeyVerifier(config.hostKeyVerifier)
      if (config.useCompression) client.useCompression()
    }
  }

  protected def connect(client: SSHClient) = {
    protect("Could not connect to") {
      client.connect(rightConfig.hostName, rightConfig.port)
      client
    }
  }

  protected def authenticate(client: SSHClient) = {
    rightConfig.login match {
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
      case e: Exception => Left("%s %s:%s due to %s".format(errorMsg, rightConfig.hostName, rightConfig.port, e))
    }
  }
}