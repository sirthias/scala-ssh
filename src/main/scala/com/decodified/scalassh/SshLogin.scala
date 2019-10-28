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

sealed trait SshLogin {
  def user: String
}

final case class PasswordLogin(user: String, passProducer: PasswordProducer) extends SshLogin

final case class PublicKeyLogin(user: String, passProducer: Option[PasswordProducer], keyfileLocations: List[String])
    extends SshLogin

object PublicKeyLogin {
  val DefaultKeyLocations: List[String] = "~/.ssh/id_rsa" :: "~/.ssh/id_dsa" :: Nil

  def apply(user: String): PublicKeyLogin =
    apply(user, None, DefaultKeyLocations)

  def apply(user: String, keyfileLocations: String*): PublicKeyLogin =
    PublicKeyLogin(user, None, keyfileLocations.toList)

  def apply(user: String, passProducer: PasswordProducer, keyfileLocations: List[String]): PublicKeyLogin =
    PublicKeyLogin(user, Some(passProducer), keyfileLocations)
}

final case class AgentLogin(user: String, host: String) extends SshLogin

object AgentLogin {
  def apply(): AgentLogin             = AgentLogin(System.getProperty("user.name"), "localhost")
  def apply(user: String): AgentLogin = AgentLogin(user, "localhost")
}
