package com.decodified.scalassh

import io.Source
import java.io.{IOException, File}

sealed trait SshLogin

case class PasswordLogin(user: String, passProducer: PasswordProducer) extends SshLogin

case class PublicKeyLogin(
  user: String,
  passProducer: Option[PasswordProducer],
  keyfileLocations: List[String]
) extends SshLogin

object PublicKeyLogin {
  def apply(user: String): PublicKeyLogin = {
    val base = System.getProperty("user.home") + File.separator + ".ssh" + File.separator
    apply(user, base + "id_rsa", base + "id_dsa")
  }
  def apply(user: String, keyfileLocations: String*): PublicKeyLogin = {
    PublicKeyLogin(user, None, keyfileLocations.toList)
  }
  def apply(user: String, passProducer: PasswordProducer, keyfileLocations: List[String]): PublicKeyLogin = {
    PublicKeyLogin(user, Some(passProducer), keyfileLocations)
  }
}

object HostFileLogin extends SshLogin {

  def loginFor(hostFile: File): Either[String, SshLogin] = {
    lines(hostFile).right.flatMap { lines =>
      splitToMap(lines, hostFile).right.flatMap { settings =>
        setting("login-type", settings, hostFile).right.flatMap {
          case "password" => passwordLogin(settings, hostFile)
          case "keyfile" => keyfileLogin(settings, hostFile)
          case x => Left("Illegal login-type setting '%s' in host file '%s': expecting either 'password' or 'keyfile'".format(x, hostFile))
        }
      }
    }
  }

  private def lines(file: File) = {
    try {
      Either.cond(
        file.exists,
        Source.fromFile(file, "utf8").getLines(),
        "Host file '%s' not found, either provide one or use a direct PasswordLogin or PublicKeyLogin".format(file)
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
    setting("username", settings, file).right.flatMap { user =>
      setting("keyfile", settings, file).right.map { keyfile =>
        val passphrase = setting("passphrase", settings, file)
        PublicKeyLogin(user, passphrase.right.toOption.map(SimplePasswordProducer), keyfile :: Nil)
      }
    }
  }

  private def setting(key: String, settings: Map[String, String], file: File) = {
    settings.get(key) match {
      case Some(user) => Right(user)
      case None => Left("Host file '%s' is missing required setting '%s'".format(file, key))
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