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
import java.io.File
import java.io.FileWriter
import io.Source
import Source.{ fromFile ⇒ open }
import org.specs2.execute.{ Failure, FailureException }

class SshClientSpec extends Specification {
  def is =
    "The SshClient should be able to" ^
      "properly connect to the test host and fetch a directory listing" ! simpleTest ^
      "properly connect to the test host and execute three independent commands" ! threeCommandsTest ^
      "properly upload to the test host" ! fileUploadTest ^
      "properly download to the test host" ! fileDownloadTest

  def simpleTest = {
    SSH(testHostName) { client ⇒
      client.exec("ls -a").right.map { result ⇒
        result.stdOutAsString() + "|" + result.stdErrAsString()
      }
    }.right.get must startWith(".\n..\n")
  }

  def threeCommandsTest = {
    SSH(testHostName) { client ⇒
      client.exec("ls").right.flatMap { res1 ⇒
        println("OK 1")
        client.exec("dfssgsdg").right.flatMap { res2 ⇒
          println("OK 2")
          client.exec("uname").right.map { res3 ⇒
            println("OK 3")
            (res1.exitCode, res2.exitCode, res3.exitCode)
          }
        }
      }
    } mustEqual Right((Some(0), Some(127), Some(0)))
  }

  def fileUploadTest = {
    val testFile = make(new File(testFileName)) { file ⇒
      val writer = new FileWriter(file)
      writer.write(testText)
      writer.close()
    }

    SSH(testHostName) { client ⇒
      client.upload(testFile.getAbsolutePath, testFileName).right.flatMap { _ ⇒
        client.exec("cat " + testFileName).right.map { result ⇒
          testFile.delete()
          result.stdOutAsString()
        }
      }
    } mustEqual Right(testText)
  }

  def fileDownloadTest = {
    SSH(testHostName) { client ⇒
      client.download(testFileName, testFileName).right.map { _ ⇒
        make(open(testFileName).getLines.mkString) { _ ⇒
          new File(testFileName).delete()
        }
      }
    } mustEqual Right(testText)
  }

  lazy val testFileName = "testUpload.txt"
  lazy val testText = "Hello, Scala SSH!"

  lazy val testHostName = {
    val fileName = HostFileConfig.DefaultHostFileDir + File.separator + ".testhost"
    try {
      Source.fromFile(fileName).getLines().toList.head
    } catch {
      case e: Exception ⇒ throw FailureException(Failure(("Could not find file '%s', you need to create it holding " +
        "nothing but the name of the test host you would like to run your tests against!").format(fileName), e.toString))
    }
  }
}
