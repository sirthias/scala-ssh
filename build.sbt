// basic
enablePlugins(AutomateHeaderPlugin)

name := "scala-ssh"
description := "A Scala library providing remote shell access via SSH"
organization := "com.decodified"
organizationHomepage := Some(new URL("http://decodified.com"))
homepage := Some(new URL("https://github.com/sirthias/scala-ssh"))
startYear := Some(2011)
licenses := Seq("Apache 2" → new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
headerLicense := Some(HeaderLicense.ALv2("2011-2018", "Mathias Doenitz"))
scmInfo := Some(ScmInfo(url("https://github.com/sirthias/scala-ssh"), "scm:git:git@github.com:sirthias/scala-ssh.git"))
developers := List(
  Developer(id = "sirthias", name = "Mathias Doenitz", email = "", url = url("http://github.com/sirthias")))

scalaVersion := "2.12.8"

javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.7", "-target", "1.7", "-Xlint:unchecked", "-Xlint:deprecation")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:_",
  "-unchecked",
  "-Xlint:_,-missing-interpolator",
  "-Xsource:2.13", // new warning: deprecate assignments in argument position
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import"
)

scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 ⇒
      Seq(
        "-Xfuture",
        "-Ywarn-inaccessible",
        "-Ywarn-infer-any",
        "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit"
      )
    case _ ⇒
      Nil
  }
}

// WORKAROUND for https://github.com/scala/bug/issues/10270
scalacOptions := {
  val orig = scalacOptions.value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v >= 12 ⇒
      orig.map {
        case "-Ywarn-unused-import" ⇒ "-Ywarn-unused:imports,-patvars,-privates,-locals,-implicits,-explicits"
        case other                  ⇒ other
      }
    case Some((2, 11)) ⇒
      orig.map {
        case "-Xsource:2.13" ⇒ ""
        case other           ⇒ other
      }
    case Some((2, 10)) ⇒ orig.takeWhile(x ⇒ !x.startsWith("-Xlint")) ++ Seq("-Xlint")
    case _             ⇒ throw new UnsupportedOperationException("unsupported version")
  }
}

// reformat main and test sources on compile
scalafmtOnCompile := true
scalafmtVersion := "1.4.0"

libraryDependencies ++= Seq(
  "com.hierynomus" % "sshj"                              % "0.27.0",
  "org.slf4j"      % "slf4j-api"                         % "1.7.25",
  "com.jcraft"     % "jsch.agentproxy.sshj"              % "0.0.9" % "provided",
  "com.jcraft"     % "jsch.agentproxy.connector-factory" % "0.0.9" % "provided",
  "ch.qos.logback" % "logback-classic"                   % "1.2.3" % "test",
  "org.scalatest"  %% "scalatest"                        % "3.0.8" % "test"
)

///////////////
// publishing
///////////////

crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.8", "2.13.0")
useGpg := true
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ ⇒
  false
}
publishTo := sonatypePublishTo.value

import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseCrossBuild := true
Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges))
