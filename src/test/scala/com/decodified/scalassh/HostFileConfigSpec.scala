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

import org.scalatest.{FreeSpec, Matchers}

class HostFileConfigSpec extends FreeSpec with Matchers {

  val config = HostResourceConfig()

  "Depending on the host file the HostFileConfig should produce a proper" - {

    "PasswordLogin" in {
      config("password.com") shouldEqual Right(
        HostConfig(PasswordLogin("bob", "123"), "password.com", enableCompression = true))
    }

    "unencrypted PublicKeyLogin" in {
      config("keyfile.com") shouldEqual Right(
        HostConfig(PublicKeyLogin("alice", "/some/file"), "xyz.special.com", port = 30))
    }

    "encrypted PublicKeyLogin" in {
      config("enc-keyfile.com") shouldEqual Right(
        HostConfig(PublicKeyLogin("alice", "superSecure", "/some/file" :: Nil), "enc-keyfile.com"))
    }

    "AgentLogin" in {
      config("agent.com") shouldEqual Right(HostConfig(AgentLogin("bob"), "agent.com", enableCompression = true))
    }

    "error message if the file is missing" in {
      config("non-existing.com").left.get shouldEqual
      ("Host resources 'non-existing.com', 'com' not found, " +
      "either provide one or use a concrete HostConfig, PasswordLogin, PublicKeyLogin or AgentLogin")
    }

    "error message if the login-type is invalid" in {
      config("invalid-login-type.com").left.get should startWith("Illegal login-type setting 'fancy pants'")
    }

    "error message if the username is missing" in {
      config("missing-user.com").left.get should endWith("is missing required setting 'username'")
    }

    "error message if the host file contains an illegal line" in {
      config("illegal-line.com").left.get should endWith("contains illegal line:\nthis line triggers an error!")
    }
  }

  "The sequence of searched config locations for host `node42.tier1.example.com` should" - {
    "be as described in the README" in {
      HostFileConfig.searchLocations("node42.tier1.example.com").toList shouldEqual
      List(
        "node42.tier1.example.com",
        "node4X.tier1.example.com",
        "nodeXX.tier1.example.com",
        "tier1.example.com",
        "tierX.example.com",
        "example.com",
        "com")
    }
  }
}
