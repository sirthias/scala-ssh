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

import java.io.{File, IOException}
import java.security.PublicKey

import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.transport.verification.{HostKeyVerifier, OpenSSHKnownHosts}
import net.schmizz.sshj.{Config, DefaultConfig}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scala.io.Source
import HostKeyVerifiers._

trait HostConfigProvider extends (String => Try[HostConfig])

object HostConfigProvider {
  implicit def fromLogin(login: SshLogin): HostConfigProvider =
    new HostConfigProvider {
      def apply(host: String) = Success(HostConfig(login = login, hostName = host))
    }
  implicit def fromHostConfig(config: HostConfig): HostConfigProvider =
    new HostConfigProvider {
      def apply(host: String) = Success(if (config.hostName.isEmpty) config.copy(hostName = host) else config)
    }
}

final case class HostConfig(login: SshLogin,
                            hostName: String = "",
                            port: Int = 22,
                            connectTimeout: Option[Int] = None,
                            connectionTimeout: Option[Int] = None,
                            commandTimeout: Option[Int] = None,
                            enableCompression: Boolean = false,
                            hostKeyVerifier: HostKeyVerifier = KnownHosts.toOption getOrElse DontVerify,
                            ptyConfig: Option[PTYConfig] = None,
                            sshjConfig: Config = HostConfig.DefaultSshjConfig)

final case class PTYConfig(term: String,
                           cols: Int,
                           rows: Int,
                           width: Int,
                           height: Int,
                           modes: java.util.Map[PTYMode, Integer])

object HostConfig {
  val DefaultSshjConfig = new DefaultConfig
}

sealed abstract class FromStringsHostConfigProvider extends HostConfigProvider {
  protected def rawLines(host: String): Try[(String, TraversableOnce[String])]

  def apply(host: String): Try[HostConfig] =
    rawLines(host).flatMap {
      case (source, lines) =>
        for {
          settings          <- splitToMap(lines, source)
          login             <- login(settings, source)
          port              <- optIntSetting("port", settings, source)
          connectTimeout    <- optIntSetting("connect-timeout", settings, source)
          connectionTimeout <- optIntSetting("connection-timeout", settings, source)
          commandTimeout    <- optIntSetting("command-timeout", settings, source)
          enableCompression <- optBoolSetting("enable-compression", settings, source)
          verifier <- setting("fingerprint", settings, source)
            .transform(x => Success(forFingerprint(x)), _ => KnownHosts)
        } yield {
          HostConfig(
            login,
            hostName = setting("host-name", settings, source).toOption getOrElse host,
            port = port getOrElse 22,
            connectTimeout = connectTimeout,
            connectionTimeout = connectionTimeout,
            commandTimeout = commandTimeout,
            enableCompression = enableCompression getOrElse false,
            hostKeyVerifier = verifier
          )
        }
    }

  private def login(settings: Map[String, String], source: String): Try[SshLogin] =
    setting("login-type", settings, source).flatMap {
      case "password" => passwordLogin(settings, source)
      case "keyfile"  => keyfileLogin(settings, source)
      case "agent"    => agentLogin(settings, source)
      case x =>
        Failure(
          SSH.Error(s"Illegal login-type setting '$x' in host config '$source': " +
            "expecting either 'password', 'keyfile' or 'agent'"))
    }

  private def passwordLogin(settings: Map[String, String], source: String): Try[PasswordLogin] =
    for {
      user <- setting("username", settings, source)
      pass <- setting("password", settings, source)
    } yield PasswordLogin(user, pass)

  private def keyfileLogin(settings: Map[String, String], source: String): Try[PublicKeyLogin] =
    for (user <- setting("username", settings, source)) yield {
      import PublicKeyLogin._
      val keyfile    = setting("keyfile", settings, source).toOption
      val passphrase = setting("passphrase", settings, source).toOption
      PublicKeyLogin(
        user,
        passphrase.map(SimplePasswordProducer),
        keyfile
          .map {
            case kf if kf.startsWith("+") => kf.tail :: DefaultKeyLocations
            case kf                       => kf :: Nil
          }
          .getOrElse(DefaultKeyLocations)
          .map(_.replaceFirst("^~/", System.getProperty("user.home") + '/').replace('/', File.separatorChar))
      )
    }

  private def agentLogin(settings: Map[String, String], source: String): Try[AgentLogin] = {
    val user = setting("username", settings, source).toOption
    val host = setting("host", settings, source).toOption
    Success(AgentLogin(user getOrElse System.getProperty("user.home"), host getOrElse "localhost"))
  }

  private def setting(key: String, settings: Map[String, String], source: String): Try[String] =
    settings.get(key) match {
      case Some(user) => Success(user)
      case None       => Failure(SSH.Error(s"Host config '$source' is missing required setting '$key'"))
    }

  private def optIntSetting(key: String, settings: Map[String, String], source: String): Try[Option[Int]] =
    setting(key, settings, source).transform(x => Success(Some(x.toInt)), _ => Success(None))

  private def optBoolSetting(key: String, settings: Map[String, String], source: String): Try[Option[Boolean]] =
    setting(key, settings, source) match {
      case Success("yes" | "YES" | "true" | "TRUE") => Success(Some(true))
      case Failure(_)                               => Success(None)
      case Success(value) =>
        Failure(SSH.Error(s"Value '$value' for setting '$key' in host config '$source' is not a legal boolean"))
    }

  private def splitToMap(lines: TraversableOnce[String], source: String) =
    lines.foldLeft(Success(Map.empty): Try[Map[String, String]]) {
      case (Success(map), line) if line.nonEmpty && line.charAt(0) != '#' =>
        line.indexOf('=') match {
          case -1 => Failure(SSH.Error(s"Host config '$source' contains illegal line:\n$line"))
          case ix => Success(map.updated(line.substring(0, ix).trim, line.substring(ix + 1).trim))
        }
      case (result, _) => result
    }
}

object HostFileConfig {
  lazy val DefaultHostFileDir     = System.getProperty("user.home") + File.separator + ".scala-ssh"
  def apply(): HostConfigProvider = apply(DefaultHostFileDir)
  def apply(hostFilesDir: String): HostConfigProvider =
    new FromStringsHostConfigProvider {
      protected def rawLines(host: String): Try[(String, TraversableOnce[String])] = {
        val locations = searchLocations(host).map(name => new File(hostFilesDir + File.separator + name))
        locations.find(_.exists) match {
          case Some(file) =>
            try Success(file.getAbsolutePath -> Source.fromFile(file, "utf8").getLines())
            catch { case e: IOException => Failure(SSH.Error(s"Could not read host file '$file' due to $e")) }
          case None =>
            Failure(
              SSH.Error(s"Host files '${locations.mkString("', '")}' not found, " +
                "either provide one or use a concrete HostConfig, PasswordLogin, PublicKeyLogin or AgentLogin"))
        }
      }
    }

  def searchLocations(name: String): Stream[String] =
    if (name.nonEmpty) {
      name #:: {
        val dotIx                           = name.indexOf('.')
        @tailrec def findDigit(i: Int): Int = if (i < 0 || name.charAt(i).isDigit) i else findDigit(i - 1)
        val digitIx                         = findDigit(if (dotIx > 0) dotIx - 1 else name.length - 1)
        if (digitIx >= 0 && digitIx < dotIx) searchLocations(name.updated(digitIx, 'X'))
        else if (dotIx > 0) searchLocations(name.substring(dotIx + 1))
        else Stream.empty
      }
    } else Stream.empty
}

object HostResourceConfig {
  def apply(): HostConfigProvider = apply("")
  def apply(resourceBase: String): HostConfigProvider =
    new FromStringsHostConfigProvider {
      protected def rawLines(host: String): Try[(String, TraversableOnce[String])] = {
        val locations = HostFileConfig.searchLocations(host).map(resourceBase + _)
        locations
          .map { location =>
            location -> {
              val inputStream = getClass.getClassLoader.getResourceAsStream(location)
              if (inputStream ne null) {
                try new StreamCopier().drainToString(inputStream).split("\n").toList.filter(_.nonEmpty)
                catch { case NonFatal(_) => Nil }
              } else Nil
            }
          }
          .find(_._2.nonEmpty) match {
          case Some(result) => Success(result)
          case None =>
            Failure(
              SSH.Error(s"Host resources '${locations.mkString("', '")}' not found, " +
                s"either provide one or use a concrete HostConfig, PasswordLogin, PublicKeyLogin or AgentLogin"))
        }
      }
    }
}

object HostKeyVerifiers {
  lazy val DontVerify: HostKeyVerifier =
    new HostKeyVerifier {
      def verify(hostname: String, port: Int, key: PublicKey) = true
    }

  lazy val KnownHosts: Try[HostKeyVerifier] = {
    val sshDir = System.getProperty("user.home") + File.separator + ".ssh" + File.separator
    fromKnownHostsFile(new File(sshDir + "known_hosts")).recoverWith {
      case NonFatal(e1) =>
        fromKnownHostsFile(new File(sshDir + "known_hosts2")).recoverWith {
          case NonFatal(e2) => Failure(SSH.Error(s"$e1 and $e2"))
        }
    }
  }

  def fromKnownHostsFile(knownHostsFile: File): Try[HostKeyVerifier] =
    if (knownHostsFile.exists()) {
      try Success(new OpenSSHKnownHosts(knownHostsFile))
      catch { case NonFatal(e) => Failure(SSH.Error(s"Could not read $knownHostsFile", e)) }
    } else Failure(SSH.Error(knownHostsFile.toString + " not found"))

  def forFingerprint(fingerprint: String): HostKeyVerifier =
    fingerprint match {
      case "any" | "ANY" => DontVerify
      case fp =>
        new HostKeyVerifier {
          def verify(hostname: String, port: Int, key: PublicKey) = SecurityUtils.getFingerprint(key) == fp
        }
    }
}
