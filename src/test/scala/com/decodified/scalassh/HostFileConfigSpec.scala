package com.decodified.scalassh

import org.specs2.Specification

class HostFileConfigSpec extends Specification { def is =

  "Depending on the host file the HostFileConfig should produce a proper" ^
    "PasswordLogin" ! {
      config("password.com") mustEqual Right(HostConfig(PasswordLogin("bob", "123"), "password.com", useCompression = true))
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
