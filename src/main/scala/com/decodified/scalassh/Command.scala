/*
 * Copyright 2011-2021 Mathias Doenitz
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

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import net.schmizz.sshj.connection.channel.direct.Session

final case class Command(command: String, input: CommandInput = CommandInput.NoInput, timeout: Option[Int] = None)

object Command {
  implicit def fromString(cmd: String): Command = Command(cmd)
}

final case class CommandInput(inputStream: Option[InputStream])

object CommandInput {
  val NoInput                                                    = CommandInput(None)
  implicit def fromByteArray(input: Array[Byte]): CommandInput   = CommandInput(Some(new ByteArrayInputStream(input)))
  implicit def fromInputStream(input: InputStream): CommandInput = CommandInput(Some(input))

  implicit def fromString(input: String, charsetName: String = "UTF8"): CommandInput =
    fromByteArray(input getBytes charsetName)
  implicit def fromFile(file: File): CommandInput = fromInputStream(new FileInputStream(file))
  def fromFileName(file: String)                  = fromFile(new File(file))
  def fromResource(resource: String)              = fromInputStream(getClass.getClassLoader getResourceAsStream resource)
}

final class CommandResult(val channel: Session.Command) {
  def stdErrStream: InputStream                    = channel.getErrorStream
  def stdOutStream: InputStream                    = channel.getInputStream
  lazy val stdErrBytes                             = new StreamCopier().drainToByteArray(stdErrStream)
  lazy val stdOutBytes                             = new StreamCopier().drainToByteArray(stdOutStream)
  def stdErrAsString(charsetname: String = "utf8") = new String(stdErrBytes, charsetname)
  def stdOutAsString(charsetname: String = "utf8") = new String(stdOutBytes, charsetname)
  lazy val exitSignal: Option[String]              = Option(channel.getExitSignal).map(_.toString)
  lazy val exitCode: Option[Int]                   = Option(channel.getExitStatus.toInt)
  lazy val exitErrorMessage: Option[String]        = Option(channel.getExitErrorMessage)
}
