package com.decodified.scalassh

import org.specs2.Specification
import java.io.File
import io.Source
import org.specs2.execute.{Failure, FailureException}

class SshClientSpec extends Specification { def is =

  "The SshClient should be able to" ^
    "properly connect to the test host and fetch a directory listing" ! simpleTest

  def simpleTest = {
    val client = new SshClient(testHostName)
    val result = client.exec("ls")
    result must beRight //) and (result.right.get.stdOutAsString() mustEqual "")
  }

  def testHostName = {
    val fileName = HostFileConfig.DefaultHostFileDir + File.separator + ".testhost"
    try {
      Source.fromFile(fileName).getLines().toList.head
    } catch {
      case e: Exception => throw FailureException(Failure(("Could not find file '%s', you need to create it holding " +
        "nothing but the name of the test host you would like to run your tests against!").format(fileName), e.toString))
    }
  }
}
