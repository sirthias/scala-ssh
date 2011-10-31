package com.decodified.scalassh

import net.schmizz.sshj.{DefaultConfig, Config}
import io.Source
import java.io.{IOException, File}
import HostKeyVerifiers._
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey
import net.schmizz.sshj.common.SecurityUtils

trait HostConfigProvider extends (String => Validated[HostConfig])

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

abstract class FromStringsHostConfigProvider extends HostConfigProvider {
  def rawLines(host: String): Validated[(String, TraversableOnce[String])]

  def apply(host: String) = {
    rawLines(host).right.flatMap { case (source, lines) =>
    splitToMap(lines, source).right.flatMap { settings =>
    login(settings, source).right.flatMap { login =>
    optIntSetting("port", settings, source).right.flatMap { port =>
    optIntSetting("connect-timeout", settings, source).right.flatMap { connectTimeout =>
    optIntSetting("connection-timeout", settings, source).right.flatMap { connectionTimeout =>
    optIntSetting("command-timeout", settings, source).right.flatMap { commandTimeout =>
    optBoolSetting("use-compression", settings, source).right.map { useCompression =>
    HostConfig(
      login,
      hostName = setting("host-name", settings, source).right.toOption.getOrElse(host),
      port = port.getOrElse(22),
      connectTimeout = connectTimeout,
      connectionTimeout = connectionTimeout,
      commandTimeout = commandTimeout,
      useCompression = useCompression.getOrElse(false),
      hostKeyVerifier = setting("fingerprint", settings, source).right.toOption.map(forFingerprint).getOrElse(DontVerify)
    )}}}}}}}}
  }

  private def login(settings: Map[String, String], source: String) = {
    setting("login-type", settings, source).right.flatMap {
      case "password" => passwordLogin(settings, source)
      case "keyfile" => keyfileLogin(settings, source)
      case x => Left("Illegal login-type setting '%s' in host config '%s': expecting either 'password' or 'keyfile'".format(x, source))
    }
  }

  private def passwordLogin(settings: Map[String, String], source: String) = {
    setting("username", settings, source).right.flatMap { user =>
      setting("password", settings, source).right.map { pass =>
        PasswordLogin(user, pass)
      }
    }
  }

  private def keyfileLogin(settings: Map[String, String], source: String) = {
    setting("username", settings, source).right.map { user =>
      val keyfile = setting("keyfile", settings, source).right.toOption
      val passphrase = setting("passphrase", settings, source).right.toOption
      PublicKeyLogin(
        user,
        passphrase.map(SimplePasswordProducer),
        keyfile.map(_ :: Nil).getOrElse(PublicKeyLogin.DefaultKeyLocations)
      )
    }
  }

  private def setting(key: String, settings: Map[String, String], source: String) = {
    settings.get(key) match {
      case Some(user) => Right(user)
      case None => Left("Host config '%s' is missing required setting '%s'".format(source, key))
    }
  }

  private def optIntSetting(key: String, settings: Map[String, String], source: String) = {
    setting(key, settings, source) match {
      case Right(value) => {
        try {
          Right(Some(value.toInt))
        } catch {
          case _: Exception => Left(("Value '%s' for setting '%s' in host config '%s' " +
            "is not a legal integer").format(value, key, source))
        }
      }
      case Left(_) => Right(None)
    }
  }

  private def optBoolSetting(key: String, settings: Map[String, String], source: String) = {
    setting(key, settings, source) match {
      case Right("yes" | "YES" | "true" | "TRUE") => Right(Some(true))
      case Right(value) => Left("Value '%s' for setting '%s' in host config '%s' is not a legal integer".format(value, key, source))
      case Left(_) => Right(None)
    }
  }

  private def splitToMap(lines: TraversableOnce[String], source: String) = {
    ((Right(Map.empty): Validated[Map[String, String]]) /: lines) {
      case (Right(map), line) if line.nonEmpty && line.charAt(0) != '#' =>
        line.indexOf('=') match {
          case -1 => Left("Host config '%s' contains illegal line:\n%s".format(source, line))
          case ix => Right(map + (line.substring(0, ix).trim -> line.substring(ix + 1).trim))
        }
      case (result, _) => result
    }
  }
}

object HostFileConfig {
  lazy val DefaultHostFileDir = System.getProperty("user.home") + File.separator + ".scala-ssh"
  def apply(): HostConfigProvider = apply(DefaultHostFileDir)
  def apply(hostFilesDir: String): HostConfigProvider = new FromStringsHostConfigProvider {
    def rawLines(host: String) = {
      val hostFile = new File(hostFilesDir + File.separator + host)
      try {
        Either.cond(
          hostFile.exists,
          hostFile.getAbsolutePath -> Source.fromFile(hostFile, "utf8").getLines(),
          "Host file '%s' not found, either provide one or use a concrete HostConfig, PasswordLogin or PublicKeyLogin".format(hostFile)
        )
      } catch {
        case e: IOException => Left("Could not read host file '%' due to %s".format(hostFile, e))
      }
    }
  }
}

object HostResourceConfig {
  def apply(): HostConfigProvider = apply("")
  def apply(resourceBase: String): HostConfigProvider = new FromStringsHostConfigProvider {
    def rawLines(host: String) = {
      val hostResource = resourceBase + host
      try {
        val resourceStream = getClass.getClassLoader.getResourceAsStream(hostResource)
        Either.cond(
          resourceStream != null,
          hostResource -> Source.fromInputStream(resourceStream, "utf8").getLines(),
          "Host resource '%s' not found".format(hostResource)
        )
      } catch {
        case e: IOException => Left("Could not read host resource '%' due to %s".format(hostResource, e))
      }
    }
  }
}

object HostKeyVerifiers {
  lazy val DontVerify = new HostKeyVerifier {
    def verify(hostname: String, port: Int, key: PublicKey) = true
  }
  def forFingerprint(fingerprint: String) = new HostKeyVerifier {
    def verify(hostname: String, port: Int, key: PublicKey) = SecurityUtils.getFingerprint(key) == fingerprint
  }
}