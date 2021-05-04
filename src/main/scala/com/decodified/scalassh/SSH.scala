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

import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Success, Try}

object SSH {

  def apply[T](host: String, configProvider: HostConfigProvider = HostFileConfig())(
      body: SshClient => Result[T]): Try[T] =
    SshClient(host, configProvider).flatMap { client =>
      try body(client).result
      catch { case NonFatal(e) => Failure(e) }
      finally client.close()
    }

  final case class Result[T](result: Try[T])

  object Result extends LowerPriorityImplicits {
    implicit def fromTry[T](value: Try[T]): Result[T] = Result(value)
  }

  abstract private[SSH] class LowerPriorityImplicits {
    implicit def fromAny[T](value: T): Result[T] = Result(Success(value))
  }

  final case class Error(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause) with NoStackTrace
}
