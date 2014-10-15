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

import annotation.tailrec
import java.io.{ ByteArrayOutputStream, OutputStream, InputStream }

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

  def emptyToString(inputStream: InputStream, charset: String = "UTF8") = {
    new String(emptyToByteArray(inputStream), charset)
  }

  def emptyToByteArray(inputStream: InputStream) = {
    val output = new ByteArrayOutputStream()
    copy(inputStream, output)
    output.toByteArray
  }
}

