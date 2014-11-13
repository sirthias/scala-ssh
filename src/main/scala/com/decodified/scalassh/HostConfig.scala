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

import net.schmizz.sshj.{ DefaultConfig, Config }
import io.Source
import java.io.{ IOException, File }
import HostKeyVerifiers._
import java.security.PublicKey
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.transport.verification.{ OpenSSHKnownHosts, HostKeyVerifier }
import annotation.tailrec

trait HostConfigProvider extends (String ⇒ Validated[HostConfig])

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
  enableCompression: Boolean = false,
  hostKeyVerifier: HostKeyVerifier = KnownHosts.right.toOption.getOrElse(DontVerify),
  ptyConfig: Option[PTYConfig] = None,
  sshjConfig: Config = HostConfig.DefaultSshjConfig)

case class PTYConfig(term: String, cols: Int, rows: Int, width: Int, height: Int, modes: java.util.Map[PTYMode, Integer])

object HostConfig {
  lazy val DefaultSshjConfig = new DefaultConfig
}

abstract class FromStringsHostConfigProvider extends HostConfigProvider {
  def rawLines(host: String): Validated[(String, TraversableOnce[String])]

  def apply(host: String) = {
    rawLines(host).right.flatMap {
      case (source, lines) ⇒
        splitToMap(lines, source).right.flatMap { settings ⇒
          login(settings, source).right.flatMap { login ⇒
            optIntSetting("port", settings, source).right.flatMap { port ⇒
              optIntSetting("connect-timeout", settings, source).right.flatMap { connectTimeout ⇒
                optIntSetting("connection-timeout", settings, source).right.flatMap { connectionTimeout ⇒
                  optIntSetting("command-timeout", settings, source).right.flatMap { commandTimeout ⇒
                    optBoolSetting("enable-compression", settings, source).right.flatMap { enableCompression ⇒
                      setting("fingerprint", settings, source).right.map(forFingerprint).left.flatMap(_ ⇒ KnownHosts).right.map { verifier ⇒
                        HostConfig(
                          login,
                          hostName = setting("host-name", settings, source).right.toOption.getOrElse(host),
                          port = port.getOrElse(22),
                          connectTimeout = connectTimeout,
                          connectionTimeout = connectionTimeout,
                          commandTimeout = commandTimeout,
                          enableCompression = enableCompression.getOrElse(false),
                          hostKeyVerifier = verifier
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
    }
  }

  private def login(settings: Map[String, String], source: String) = {
    setting("login-type", settings, source).right.flatMap {
      case "password" ⇒ passwordLogin(settings, source)
      case "keyfile"  ⇒ keyfileLogin(settings, source)
      case "agent"    ⇒ agentLogin(settings, source)
      case x          ⇒ Left("Illegal login-type setting '%s' in host config '%s': expecting either 'password' or 'keyfile'".format(x, source))
    }
  }

  private def passwordLogin(settings: Map[String, String], source: String) = {
    setting("username", settings, source).right.flatMap { user ⇒
      setting("password", settings, source).right.map { pass ⇒
        PasswordLogin(user, pass)
      }
    }
  }

  private def keyfileLogin(settings: Map[String, String], source: String) = {
    import PublicKeyLogin._
    setting("username", settings, source).right.map { user ⇒
      val keyfile = setting("keyfile", settings, source).right.toOption
      val passphrase = setting("passphrase", settings, source).right.toOption
      PublicKeyLogin(
        user,
        passphrase.map(SimplePasswordProducer),
        keyfile.map {
          case kf if kf.startsWith("+") ⇒ kf.tail :: DefaultKeyLocations
          case kf                       ⇒ kf :: Nil
        }.getOrElse(DefaultKeyLocations).map(
          _.replaceFirst("^~/", System.getProperty("user.home") + '/').replace('/', File.separatorChar)
        )
      )
    }
  }

  private def agentLogin(settings: Map[String, String], source: String) = {
    val user = setting("username", settings, source).right.toOption
    val host = setting("host", settings, source).right.toOption
    Right(AgentLogin(user.getOrElse(System.getProperty("user.home")), host.getOrElse("localhost")))
  }

  private def setting(key: String, settings: Map[String, String], source: String) = {
    settings.get(key) match {
      case Some(user) ⇒ Right(user)
      case None       ⇒ Left("Host config '%s' is missing required setting '%s'".format(source, key))
    }
  }

  private def optIntSetting(key: String, settings: Map[String, String], source: String) = {
    setting(key, settings, source) match {
      case Right(value) ⇒
        try Right(Some(value.toInt))
        catch {
          case _: NumberFormatException ⇒ Left(("Value '%s' for setting '%s' in host config '%s' " +
            "is not a legal integer").format(value, key, source))
        }
      case Left(_) ⇒ Right(None)
    }
  }

  private def optBoolSetting(key: String, settings: Map[String, String], source: String) = {
    setting(key, settings, source) match {
      case Right("yes" | "YES" | "true" | "TRUE") ⇒ Right(Some(true))
      case Right(value)                           ⇒ Left("Value '%s' for setting '%s' in host config '%s' is not a legal integer".format(value, key, source))
      case Left(_)                                ⇒ Right(None)
    }
  }

  private def splitToMap(lines: TraversableOnce[String], source: String) = {
    ((Right(Map.empty): Validated[Map[String, String]]) /: lines) {
      case (Right(map), line) if line.nonEmpty && line.charAt(0) != '#' ⇒
        line.indexOf('=') match {
          case -1 ⇒ Left("Host config '%s' contains illegal line:\n%s".format(source, line))
          case ix ⇒ Right(map + (line.substring(0, ix).trim -> line.substring(ix + 1).trim))
        }
      case (result, _) ⇒ result
    }
  }
}

object HostFileConfig {
  lazy val DefaultHostFileDir = System.getProperty("user.home") + File.separator + ".scala-ssh"
  def apply(): HostConfigProvider = apply(DefaultHostFileDir)
  def apply(hostFilesDir: String): HostConfigProvider = new FromStringsHostConfigProvider {
    def rawLines(host: String) = {
      val locations = searchLocations(host).map(name ⇒ new File(hostFilesDir + File.separator + name))
      locations.find(_.exists) match {
        case Some(file) ⇒
          try Right(file.getAbsolutePath -> Source.fromFile(file, "utf8").getLines())
          catch { case e: IOException ⇒ Left("Could not read host file '%s' due to %s".format(file, e)) }
        case None ⇒
          Left(("Host files '%s' not found, either provide one or use a concrete HostConfig, PasswordLogin, " +
            "PublicKeyLogin or AgentLogin").format(locations.mkString("', '")))
      }
    }
  }

  def searchLocations(name: String): Stream[String] = {
    if (name.isEmpty) Stream.empty
    else name #:: {
      val dotIx = name.indexOf('.')
      @tailrec def findDigit(i: Int): Int = if (i < 0 || name.charAt(i).isDigit) i else findDigit(i - 1)
      val digitIx = findDigit(if (dotIx > 0) dotIx - 1 else name.length - 1)
      if (digitIx >= 0 && digitIx < dotIx)
        searchLocations(name.updated(digitIx, 'X'))
      else if (dotIx > 0)
        searchLocations(name.substring(dotIx + 1))
      else Stream.empty
    }
  }
}

object HostResourceConfig {
  def apply(): HostConfigProvider = apply("")
  def apply(resourceBase: String): HostConfigProvider = new FromStringsHostConfigProvider {
    def rawLines(host: String) = {
      val locations = HostFileConfig.searchLocations(host).map(resourceBase + _)
      locations.map { r ⇒
        r -> {
          val inputStream = getClass.getClassLoader.getResourceAsStream(r)
          try new StreamCopier().emptyToString(inputStream).split("\n").toList
          catch { case _: Exception ⇒ null }
        }
      }.find(_._2 != null) match {
        case Some(result) ⇒ Right(result)
        case None ⇒
          Left(("Host resources '%s' not found, either provide one or use a concrete HostConfig, PasswordLogin, " +
            "PublicKeyLogin or AgentLogin").format(locations.mkString("', '")))
      }
    }
  }
}

object HostKeyVerifiers {
  lazy val DontVerify = new HostKeyVerifier {
    def verify(hostname: String, port: Int, key: PublicKey) = true
  }
  lazy val KnownHosts = {
    val sshDir = System.getProperty("user.home") + File.separator + ".ssh" + File.separator
    fromKnownHostsFile(new File(sshDir + "known_hosts")).left.flatMap { error1 ⇒
      fromKnownHostsFile(new File(sshDir + "known_hosts2")).left.map(error1 + " and " + _)
    }
  }
  def fromKnownHostsFile(knownHostsFile: File): Validated[HostKeyVerifier] = {
    if (knownHostsFile.exists()) {
      try { Right(new OpenSSHKnownHosts(knownHostsFile)) }
      catch { case e: Exception ⇒ Left("Could not read %s due to %s".format(knownHostsFile, e)) }
    } else Left(knownHostsFile.toString + " not found")
  }
  def forFingerprint(fingerprint: String) = fingerprint match {
    case "any" | "ANY" ⇒ DontVerify
    case fp ⇒ new HostKeyVerifier {
      def verify(hostname: String, port: Int, key: PublicKey) = SecurityUtils.getFingerprint(key) == fp
    }
  }
}
