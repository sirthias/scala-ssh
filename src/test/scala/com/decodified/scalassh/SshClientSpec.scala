/*
 * Copyright 2011-2020 Mathias Doenitz
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

import java.io.{File, FileWriter}

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import scala.io.Source.{fromFile => open}
import scala.util.Success
import scala.util.control.NonFatal

class SshClientSpec extends AnyFreeSpec with Matchers {

  val testFileName = "testUpload.txt"
  val testText     = "Hello, Scala SSH!"

  val testHostName = {
    val fileName = HostFileConfig.DefaultHostFileDir + File.separator + ".testhost"
    val source   = Source.fromFile(fileName)
    try source.getLines().toList.head
    catch {
      case NonFatal(e) =>
        fail(
          s"Could not find file '$fileName', you need to create it holding nothing but the name of the " +
            s"test host you would like to run your tests against!",
          e)
    } finally source.close()
  }

  "The SshClient should be able to" - {

    "properly connect to the test host and fetch a directory listing" in {
      SSH(testHostName) { client =>
        client.exec("ls -a").map(result => result.stdOutAsString() + "|" + result.stdErrAsString())
      }.get should startWith(".\n..\n")
    }

    "properly connect to the test host and execute three independent commands" in {
      SSH(testHostName) { client =>
        for {
          res1 <- client.exec("ls")
          _ = println("OK 1")
          res2 <- client.exec("dfssgsdg")
          _ = println("OK 2")
          res3 <- client.exec("uname")
          _ = println("OK 3")
        } yield (res1.exitCode, res2.exitCode, res3.exitCode)
      } shouldEqual Success((Some(0), Some(127), Some(0)))
    }

    "properly upload to the test host" in {
      val testFile = new File(testFileName)
      val writer   = new FileWriter(testFile)
      writer.write(testText)
      writer.close()

      try {
        SSH(testHostName) { client =>
          for {
            _      <- client.upload(testFile.getAbsolutePath, testFileName)
            result <- client.exec("cat " + testFileName)
          } yield result.stdOutAsString()
        } shouldEqual Success(testText)
      } finally testFile.delete()
    }

    "properly download from the test host" in {
      SSH(testHostName) { client =>
        for {
          _ <- client.download(testFileName, testFileName)
        } yield {
          try open(testFileName).getLines().mkString
          finally new File(testFileName).delete()
        }
      } shouldEqual Success(testText)
    }
  }
}
