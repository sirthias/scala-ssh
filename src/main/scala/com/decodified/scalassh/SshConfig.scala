package com.decodified.scalassh

import java.io.File
import net.schmizz.sshj.{DefaultConfig, Config}

case class SshConfig(
  connectTimeout: Option[Int] = None,
  connectionTimeout: Option[Int] = None,
  commandTimeout: Option[Int] = None,
  useCompression: Boolean = false,
  hostFilesDir: String = SshConfig.DefaultHostFileDir,
  sshjConfig: Config = new DefaultConfig
)

object SshConfig {
  lazy val DefaultHostFileDir = System.getProperty("user.home") + File.separator + ".scala-ssh"
}


























