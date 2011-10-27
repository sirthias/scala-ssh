package com.decodified.scalassh

import net.schmizz.sshj.{DefaultConfig, Config}
import io.Source
import java.io.{IOException, File}
import HostKeyVerifiers._
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey
import net.schmizz.sshj.common.SecurityUtils

trait HostConfigProvider extends (String => Either[String, HostConfig])

object HostConfigProvider {
  implicit def login2HostConfigProvider(login: SshLogin) = new HostConfigProvider {
    def apply(host: String) = Right(HostConfig(login = login, hostName = host))
  }
  implicit def hostConfig2HostConfigProvider(config: HostConfig) = new HostConfigProvider {
    def apply(host: String) = Right(if (config.hostName.isEmpty) config.copy(hostName = host) else config)
  }
}

case class HostConfig(
  login: SshLogin,
  hostName: String = "",
  port: Int = 22,
  connectTimeout: Option[Int] = None,
  connectionTimeout: Option[Int] = None,
  commandTimeout: Option[Int] = None,
  useCompression: Boolean = false,
  hostKeyVerifier: HostKeyVerifier = DontVerify,
  sshjConfig: Config = HostConfig.DefaultSshjConfig
)

object HostConfig {
  lazy val DefaultSshjConfig = new DefaultConfig
}

class HostFileConfig(hostFilesDir: String) extends HostConfigProvider {
  def apply(host: String) = {
    val hostFile = new File(hostFilesDir + File.separator + host)
    lines(hostFile).right.flatMap { lines =>
    splitToMap(lines, hostFile).right.flatMap { settings =>
    login(settings, hostFile).right.flatMap { login =>
    optIntSetting("port", settings, hostFile).right.flatMap { port =>
    optIntSetting("connect-timeout", settings, hostFile).right.flatMap { connectTimeout =>
    optIntSetting("connection-timeout", settings, hostFile).right.flatMap { connectionTimeout =>
    optIntSetting("command-timeout", settings, hostFile).right.flatMap { commandTimeout =>
    optBoolSetting("use-compression", settings, hostFile).right.map { useCompression =>
    HostConfig(
      login,
      hostName = setting("host-name", settings, hostFile).right.toOption.getOrElse(host),
      port = port.getOrElse(22),
      connectTimeout = connectTimeout,
      connectionTimeout = connectionTimeout,
      commandTimeout = commandTimeout,
      useCompression = useCompression.getOrElse(false),
      hostKeyVerifier = setting("fingerprint", settings, hostFile).right.toOption.map(forFingerprint).getOrElse(DontVerify)
    )}}}}}}}}
  }

  private def login(settings: Map[String, String], file: File) = {
    setting("login-type", settings, file).right.flatMap {
      case "password" => passwordLogin(settings, file)
      case "keyfile" => keyfileLogin(settings, file)
      case x => Left("Illegal login-type setting '%s' in host file '%s': expecting either 'password' or 'keyfile'".format(x, file))
    }
  }

  private def lines(file: File) = {
    try {
      Either.cond(
        file.exists,
        Source.fromFile(file, "utf8").getLines(),
        "Host file '%s' not found, either provide one or use a concrete HostConfig, PasswordLogin or PublicKeyLogin".format(file)
      )
    } catch {
      case e: IOException => Left("Could not read host file '%' due to %s".format(file, e))
    }
  }

  private def passwordLogin(settings: Map[String, String], file: File) = {
    setting("username", settings, file).right.flatMap { user =>
      setting("password", settings, file).right.map { pass =>
        PasswordLogin(user, pass)
      }
    }
  }

  private def keyfileLogin(settings: Map[String, String], file: File) = {
    setting("username", settings, file).right.map { user =>
      val keyfile = setting("keyfile", settings, file).right.toOption
      val passphrase = setting("passphrase", settings, file).right.toOption
      PublicKeyLogin(
        user,
        passphrase.map(SimplePasswordProducer),
        keyfile.map(_ :: Nil).getOrElse(PublicKeyLogin.DefaultKeyLocations)
      )
    }
  }

  private def setting(key: String, settings: Map[String, String], file: File) = {
    settings.get(key) match {
      case Some(user) => Right(user)
      case None => Left("Host file '%s' is missing required setting '%s'".format(file, key))
    }
  }

  private def optIntSetting(key: String, settings: Map[String, String], file: File) = {
    setting(key, settings, file) match {
      case Right(value) => {
        try {
          Right(Some(value.toInt))
        } catch {
          case _: Exception => Left("Value '%s' for setting '%s' in host file '%s' is not a legal integer".format(value, key, file))
        }
      }
      case Left(_) => Right(None)
    }
  }

  private def optBoolSetting(key: String, settings: Map[String, String], file: File) = {
    setting(key, settings, file) match {
      case Right("yes" | "YES" | "true" | "TRUE") => Right(Some(true))
      case Right(value) => Left("Value '%s' for setting '%s' in host file '%s' is not a legal integer".format(value, key, file))
      case Left(_) => Right(None)
    }
  }

  private def splitToMap(lines: Iterator[String], file: File) = {
    ((Right(Map.empty): Either[String, Map[String, String]]) /: lines) {
      case (Right(map), line) if line.nonEmpty && line.charAt(0) != '#' =>
        line.indexOf('=') match {
          case -1 => Left("Host file '%s' contains illegal line:\n%s".format(file, line))
          case ix => Right(map + (line.substring(0, ix).trim -> line.substring(ix + 1).trim))
        }
      case (result, _) => result
    }
  }
}

object HostFileConfig {
  lazy val DefaultHostFileDir = System.getProperty("user.home") + File.separator + ".scala-ssh"
}

object HostKeyVerifiers {
  lazy val DontVerify = new HostKeyVerifier {
    def verify(hostname: String, port: Int, key: PublicKey) = true
  }
  def forFingerprint(fingerprint: String) = new HostKeyVerifier {
    def verify(hostname: String, port: Int, key: PublicKey) = SecurityUtils.getFingerprint(key) == fingerprint
  }
}