package com.decodified.scalassh

import annotation.tailrec
import java.io.{ByteArrayOutputStream, OutputStream, InputStream}

final class StreamCopier(bufferSize: Int = 4096) {
  private val buffer = new Array[Byte](bufferSize)

  @tailrec
  def copy(in: InputStream, out: OutputStream) {
    val bytes = in.read(buffer)
    if (bytes > 0) {
      out.write(buffer, 0, bytes)
      copy(in, out)
    } else {
      in.close()
      out.close()
    }
  }

  def emptyToByteArray(inputStream: InputStream) = {
    val output = new ByteArrayOutputStream()
    copy(inputStream, output)
    output.toByteArray
  }
}



























