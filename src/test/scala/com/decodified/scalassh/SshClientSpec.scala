package com.decodified.scalassh

import org.specs2.Specification
import java.io.File
import io.Source
import org.specs2.execute.{Failure, FailureException}

class SshClientSpec extends Specification { def is =

  "The SshClient should be able to" ^
    "properly connect to the test host and fetch a directory listing"           ! simpleTest^
    "properly connect to the test host and execute three independent commands"  ! threeCommandsTest

  def simpleTest = {
    SSH(testHostName) { client =>
      client.exec("ls -a").right.map { result =>
        result.stdOutAsString() + "|" + result.stdErrAsString()
      }
    }.right.get must startWith(".\n..\n")
  }

  def threeCommandsTest = {
    SSH(testHostName) { client =>
      client.exec("ls").right.flatMap { res1 =>
        println("OK 1")
        client.exec("dfssgsdg").right.flatMap { res2 =>
          println("OK 2")
          client.exec("uname").right.map { res3 =>
            println("OK 3")
            (res1.exitCode, res2.exitCode, res3.exitCode)
          }
        }
      }
    }.right.get mustEqual (Some(0), Some(127), Some(0))
  }

  lazy val testHostName = {
    val fileName = HostFileConfig.DefaultHostFileDir + File.separator + ".testhost"
    try {
      Source.fromFile(fileName).getLines().toList.head
    } catch {
      case e: Exception => throw FailureException(Failure(("Could not find file '%s', you need to create it holding " +
        "nothing but the name of the test host you would like to run your tests against!").format(fileName), e.toString))
    }
  }
}
