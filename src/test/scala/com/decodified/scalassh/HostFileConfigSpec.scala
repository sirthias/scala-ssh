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

import org.specs2.Specification

class HostFileConfigSpec extends Specification { def is =

  "Depending on the host file the HostFileConfig should produce a proper" ^
    "PasswordLogin" ! {
      config("password.com") mustEqual Right(HostConfig(PasswordLogin("bob", "123"), "password.com", enableCompression = true))
    } ^
    "unencrypted PublicKeyLogin" ! {
      config("keyfile.com") mustEqual Right(HostConfig(PublicKeyLogin("alice", "/some/file"), "xyz.special.com", port = 30))
    }^
    "encrypted PublicKeyLogin" ! {
      config("enc-keyfile.com") mustEqual Right(HostConfig(PublicKeyLogin("alice", "superSecure", "/some/file" :: Nil), "enc-keyfile.com"))
    } ^
    "error message if the file is missing" ! {
      config("non-existing.com").left.get mustEqual "Host resource 'non-existing.com' not found"
    } ^
    "error message if the login-type is invalid" ! {
      config("invalid-login-type.com").left.get must startingWith("Illegal login-type setting 'fancy pants'")
    } ^
    "error message if the username is missing" ! {
      config("missing-user.com").left.get must endWith("is missing required setting 'username'")
    } ^
    "error message if the host file contains an illegal line" ! {
      config("illegal-line.com").left.get must endWith("contains illegal line:\nthis line triggers an error!")
    }

  val config = HostResourceConfig()
}
