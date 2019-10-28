/*
 * Copyright 2011-2019 Mathias Doenitz
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

import scala.annotation.tailrec
import java.io.{ByteArrayOutputStream, InputStream, OutputStream}

final class StreamCopier(bufferSize: Int = 4096) {

  def copy(in: InputStream, out: OutputStream): Unit = {
    val buffer = new Array[Byte](bufferSize)
    @tailrec def rec(): Unit = {
      val bytes = in.read(buffer)
      if (bytes > 0) {
        out.write(buffer, 0, bytes)
        rec()
      }
    }
    try rec()
    finally {
      in.close()
      out.close()
    }
  }

  def drainToString(inputStream: InputStream, charset: String = "UTF8"): String =
    new String(drainToByteArray(inputStream), charset)

  def drainToByteArray(inputStream: InputStream): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    copy(inputStream, output)
    output.toByteArray
  }
}
