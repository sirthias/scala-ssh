package com.decodified.scalassh

import org.specs2.Specification
import java.io.File

class HostFileLoginSpec extends Specification { def is =

  "Depending on the host file the HostFileLogin should produce a proper" ^
    "PasswordLogin" ! {
      HostFileLogin.loginFor(file("password.com")) mustEqual Right(PasswordLogin("bob", "123"))
    } ^
    "unencrypted PublicKeyLogin" ! {
      HostFileLogin.loginFor(file("keyfile.com")) mustEqual Right(PublicKeyLogin("alice", "/some/file"))
    }^
    "encrypted PublicKeyLogin" ! {
      HostFileLogin.loginFor(file("enc-keyfile.com")) mustEqual Right(PublicKeyLogin("alice", "superSecure", "/some/file" :: Nil))
    } ^
    "error message if the file is missing" ! {
      HostFileLogin.loginFor(new File("non-existing.com")) mustEqual
        Left("Host file 'non-existing.com' not found, either provide one or use a direct PasswordLogin or PublicKeyLogin")
    } ^
    "error message if the login-type is invalid" ! {
      val f = file("invalid-login-type.com")
      HostFileLogin.loginFor(f) mustEqual
        Left("Illegal login-type setting 'fancy pants' in host file '" + f + "': expecting either 'password' or 'keyfile'")
    } ^
    "error message if the username is missing" ! {
      val f = file("missing-user.com")
      HostFileLogin.loginFor(f) mustEqual Left("Host file '" + f + "' is missing required setting 'username'")
    }


  def file(resource: String) = new File(getClass.getClassLoader.getResource(resource).toURI)
}
