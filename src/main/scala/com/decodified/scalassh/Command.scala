package com.decodified.scalassh

import java.io.{ByteArrayInputStream, InputStream}
import net.schmizz.sshj.connection.channel.direct.Session

case class Command(command: String, input: CommandInput = CommandInput.NoInput, timeout: Option[Int])

case class CommandInput(inputStream: Option[InputStream])

object CommandInput {
  lazy val NoInput = CommandInput(None)
  implicit def apply(input: String, charsetName: String = "UTF8"): CommandInput = apply(input.getBytes(charsetName))
  implicit def apply(input: Array[Byte]): CommandInput = apply(Some(new ByteArrayInputStream(input)))
}

class CommandResult(val channel: Session.Command) {
  def stdErrStream: InputStream = channel.getErrorStream
  def stdOutStream: InputStream = channel.getInputStream
  lazy val stdErrBytes = new StreamCopier().emptyToByteArray(stdErrStream)
  lazy val stdOutBytes = new StreamCopier().emptyToByteArray(stdOutStream)
  def stdErrAsString(charsetname: String = "utf8") = new String(stdErrBytes, charsetname)
  def stdOutAsString(charsetname: String = "utf8") = new String(stdErrBytes, charsetname)
  lazy val exitSignal: Option[String] = Option(channel.getExitSignal).map(_.toString)
  lazy val exitCode: Option[Int] = Option(channel.getExitStatus)
  lazy val exitErrorMessage: Option[String] = Option(channel.getExitErrorMessage)
}