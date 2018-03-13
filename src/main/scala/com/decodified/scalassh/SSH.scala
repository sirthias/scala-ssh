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

import scala.util.control.NonFatal

object SSH {

  def apply[T](host: String, configProvider: HostConfigProvider = HostFileConfig())(
      body: SshClient ⇒ Result[T]): Validated[T] =
    SshClient(host, configProvider).right.flatMap { client ⇒
      val result = {
        try body(client).result
        catch { case NonFatal(e) ⇒ Left(e.toString) }
      }
      client.close()
      result
    }

  final case class Result[T](result: Validated[T])

  object Result extends LowerPriorityImplicits {
    implicit def fromValidated[T](value: Validated[T]) = Result(value)
  }
  private[SSH] abstract class LowerPriorityImplicits {
    implicit def fromAny[T](value: T) = Result(Right(value))
  }
}
