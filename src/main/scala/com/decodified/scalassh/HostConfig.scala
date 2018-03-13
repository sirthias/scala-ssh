/*
 * Copyright (C) 2011 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.decodified.scalassh

import net.schmizz.sshj.{Config, DefaultConfig}

import io.Source
import java.io.{File, IOException}

import HostKeyVerifiers._
import java.security.PublicKey

import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.transport.verification.{HostKeyVerifier, OpenSSHKnownHosts}

import annotation.tailrec
import scala.util.control.NonFatal

trait HostConfigProvider extends (String ⇒ Validated[HostConfig])

object HostConfigProvider {
  implicit def fromLogin(login: SshLogin): HostConfigProvider =
    new HostConfigProvider {
      def apply(host: String) = Right(HostConfig(login = login, hostName = host))
    }
  implicit def fromHostConfig(config: HostConfig): HostConfigProvider =
    new HostConfigProvider {
      def apply(host: String) = Right(if (config.hostName.isEmpty) config.copy(hostName = host) else config)
    }
}

final case class HostConfig(login: SshLogin,
                            hostName: String = "",
                            port: Int = 22,
                            connectTimeout: Option[Int] = None,
                            connectionTimeout: Option[Int] = None,
                            commandTimeout: Option[Int] = None,
                            enableCompression: Boolean = false,
                            hostKeyVerifier: HostKeyVerifier = KnownHosts.right.toOption getOrElse DontVerify,
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
  protected def rawLines(host: String): Validated[(String, TraversableOnce[String])]

  def apply(host: String): Validated[HostConfig] =
    rawLines(host).right.flatMap {
      case (source, lines) ⇒
        for {
          settings          ← splitToMap(lines, source).right
          login             ← login(settings, source).right
          port              ← optIntSetting("port", settings, source).right
          connectTimeout    ← optIntSetting("connect-timeout", settings, source).right
          connectionTimeout ← optIntSetting("connection-timeout", settings, source).right
          commandTimeout    ← optIntSetting("command-timeout", settings, source).right
          enableCompression ← optBoolSetting("enable-compression", settings, source).right
          verifier ← setting("fingerprint", settings, source).right
            .map(forFingerprint)
            .left
            .flatMap(_ ⇒ KnownHosts)
            .right
        } yield {
          HostConfig(
            login,
            hostName = setting("host-name", settings, source).right.toOption getOrElse host,
            port = port getOrElse 22,
            connectTimeout = connectTimeout,
            connectionTimeout = connectionTimeout,
            commandTimeout = commandTimeout,
            enableCompression = enableCompression getOrElse false,
            hostKeyVerifier = verifier
          )
        }
    }

  private def login(settings: Map[String, String], source: String): Validated[SshLogin] =
    setting("login-type", settings, source).right.flatMap {
      case "password" ⇒ passwordLogin(settings, source)
      case "keyfile"  ⇒ keyfileLogin(settings, source)
      case "agent"    ⇒ agentLogin(settings, source)
      case x ⇒
        Left(
          "Illegal login-type setting '%s' in host config '%s': expecting either 'password' or 'keyfile'"
            .format(x, source))
    }

  private def passwordLogin(settings: Map[String, String], source: String): Validated[PasswordLogin] =
    for {
      user ← setting("username", settings, source).right
      pass ← setting("password", settings, source).right
    } yield PasswordLogin(user, pass)

  private def keyfileLogin(settings: Map[String, String], source: String): Validated[PublicKeyLogin] =
    setting("username", settings, source).right.map { user ⇒
      import PublicKeyLogin._
      val keyfile    = setting("keyfile", settings, source).right.toOption
      val passphrase = setting("passphrase", settings, source).right.toOption
      PublicKeyLogin(
        user,
        passphrase.map(SimplePasswordProducer),
        keyfile
          .map {
            case kf if kf.startsWith("+") ⇒ kf.tail :: DefaultKeyLocations
            case kf                       ⇒ kf :: Nil
          }
          .getOrElse(DefaultKeyLocations)
          .map(_.replaceFirst("^~/", System.getProperty("user.home") + '/').replace('/', File.separatorChar))
      )
    }

  private def agentLogin(settings: Map[String, String], source: String): Validated[AgentLogin] = {
    val user = setting("username", settings, source).right.toOption
    val host = setting("host", settings, source).right.toOption
    Right(AgentLogin(user getOrElse System.getProperty("user.home"), host getOrElse "localhost"))
  }

  private def setting(key: String, settings: Map[String, String], source: String): Validated[String] =
    settings.get(key) match {
      case Some(user) ⇒ Right(user)
      case None       ⇒ Left(s"Host config '$source' is missing required setting '$key'")
    }

  private def optIntSetting(key: String, settings: Map[String, String], source: String): Validated[Option[Int]] = {
    setting(key, settings, source) match {
      case Right(value) ⇒
        try Right(Some(value.toInt))
        catch {
          case _: NumberFormatException ⇒
            Left(s"Value '$value' for setting '$key' in host config '$source' is not a legal integer")
        }
      case Left(_) ⇒ Right(None)
    }
  }

  private def optBoolSetting(key: String, settings: Map[String, String], source: String): Validated[Option[Boolean]] =
    setting(key, settings, source) match {
      case Right("yes" | "YES" | "true" | "TRUE") ⇒ Right(Some(true))
      case Right(value)                           ⇒ Left(s"Value '$value' for setting '$key' in host config '$source' is not a legal integer")
      case Left(_)                                ⇒ Right(None)
    }

  private def splitToMap(lines: TraversableOnce[String], source: String) =
    lines.foldLeft(Right(Map.empty): Validated[Map[String, String]]) {
      case (Right(map), line) if line.nonEmpty && line.charAt(0) != '#' ⇒
        line.indexOf('=') match {
          case -1 ⇒ Left(s"Host config '$source' contains illegal line:\n$line")
          case ix ⇒ Right(map.updated(line.substring(0, ix).trim, line.substring(ix + 1).trim))
        }
      case (result, _) ⇒ result
    }
}

object HostFileConfig {
  lazy val DefaultHostFileDir     = System.getProperty("user.home") + File.separator + ".scala-ssh"
  def apply(): HostConfigProvider = apply(DefaultHostFileDir)
  def apply(hostFilesDir: String): HostConfigProvider =
    new FromStringsHostConfigProvider {
      protected def rawLines(host: String) = {
        val locations = searchLocations(host).map(name ⇒ new File(hostFilesDir + File.separator + name))
        locations.find(_.exists) match {
          case Some(file) ⇒
            try Right(file.getAbsolutePath → Source.fromFile(file, "utf8").getLines())
            catch { case e: IOException ⇒ Left(s"Could not read host file '$file' due to $e") }
          case None ⇒
            Left(
              s"Host files '${locations.mkString("', '")}' not found, " +
                "either provide one or use a concrete HostConfig, PasswordLogin, PublicKeyLogin or AgentLogin")
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
      protected def rawLines(host: String): Validated[(String, TraversableOnce[String])] = {
        val locations = HostFileConfig.searchLocations(host).map(resourceBase + _)
        locations
          .map { location ⇒
            location → {
              val inputStream = getClass.getClassLoader.getResourceAsStream(location)
              if (inputStream ne null) {
                try new StreamCopier().drainToString(inputStream).split("\n").toList.filter(_.nonEmpty)
                catch { case NonFatal(_) ⇒ Nil }
              } else Nil
            }
          }
          .find(_._2.nonEmpty) match {
          case Some(result) ⇒ Right(result)
          case None ⇒
            Left(
              s"Host resources '${locations.mkString("', '")}' not found, " +
                s"either provide one or use a concrete HostConfig, PasswordLogin, PublicKeyLogin or AgentLogin")
        }
      }
    }
}

object HostKeyVerifiers {
  lazy val DontVerify: HostKeyVerifier =
    new HostKeyVerifier {
      def verify(hostname: String, port: Int, key: PublicKey) = true
    }

  lazy val KnownHosts: Validated[HostKeyVerifier] = {
    val sshDir = System.getProperty("user.home") + File.separator + ".ssh" + File.separator
    for {
      error1 ← fromKnownHostsFile(new File(sshDir + "known_hosts")).left
      error2 ← fromKnownHostsFile(new File(sshDir + "known_hosts2")).left
    } yield error1 + " and " + error2
  }

  def fromKnownHostsFile(knownHostsFile: File): Validated[HostKeyVerifier] =
    if (knownHostsFile.exists()) {
      try Right(new OpenSSHKnownHosts(knownHostsFile))
      catch { case NonFatal(e) ⇒ Left(s"Could not read $knownHostsFile due to $e") }
    } else Left(knownHostsFile.toString + " not found")

  def forFingerprint(fingerprint: String): HostKeyVerifier =
    fingerprint match {
      case "any" | "ANY" ⇒ DontVerify
      case fp ⇒
        new HostKeyVerifier {
          def verify(hostname: String, port: Int, key: PublicKey) = SecurityUtils.getFingerprint(key) == fp
        }
    }
}
