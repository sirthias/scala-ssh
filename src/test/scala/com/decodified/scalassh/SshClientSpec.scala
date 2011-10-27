package com.decodified.scalassh

import org.specs2.Specification
import java.io.File
import io.Source
import org.specs2.execute.{Failure, FailureException}

class SshClientSpec extends Specification { def is =

  "The SshClient should be able to" ^
    "properly connect to the test host and fetch a directory listing" ! simpleTest

  def simpleTest = {
    val host = testHostName
    val hostFile = new File(SshConfig.DefaultHostFileDir + File.separator + host)
    val client = new SshClient(SshEndpoint())
  }

  def testHostName = {
    val fileName = SshConfig.DefaultHostFileDir + File.separator + ".testhost"
    try {
      Source.fromFile(fileName).getLines().toList.head
    } catch {
      case e: Exception => throw FailureException(Failure("Could not find .testhost file,\nyou need to create '" +
        fileName + "' containing nothing but the name of the test host you would like to run your tests against!",
        e.toString))
    }
  }
}
