// basic
name := "scala-ssh"
description := "A Scala library providing remote shell access via SSH"
version := "0.8.0"
organization := "com.decodified"
organizationHomepage := Some(new URL("http://decodified.com"))
homepage := Some(new URL("https://github.com/sirthias/scala-ssh"))
startYear := Some(2011)
licenses := Seq("Apache 2" → new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
scmInfo := Some(ScmInfo(url("https://github.com/sirthias/scala-ssh"), "scm:git:git@github.com:sirthias/scala-ssh.git"))
developers := List(
  Developer(id = "sirthias", name = "Mathias Doenitz", email = "", url = url("http://github.com/sirthias")))

scalaVersion := "2.11.12"

javacOptions ++= Seq("-encoding", "UTF-8", "-source", "1.7", "-target", "1.7", "-Xlint:unchecked", "-Xlint:deprecation")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:_",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint:_,-missing-interpolator",
  "-Xfuture",
  "-Xsource:2.13", // new warning: deprecate assignments in argument position
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import"
)

// WORKAROUND for https://github.com/scala/bug/issues/10270
scalacOptions := {
  val orig = scalacOptions.value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) ⇒
      orig.map {
        case "-Xlint" ⇒ "-Xlint:-unused,_"
        case "-Ywarn-unused-import" ⇒
          "-Ywarn-unused:imports,-patvars,-privates,-locals,-implicits,-explicits"
        case other ⇒ other
      }
    case Some((2, 11)) ⇒
      orig.map {
        case "-Xsource:2.13" ⇒ ""
        case other           ⇒ other
      }
    case Some((2, 10)) ⇒
      orig.map {
        case "-Xsource:2.13" ⇒ ""
        case other           ⇒ other
      }
    case _ ⇒ throw new UnsupportedOperationException("unsupported version")
  }
}

// reformat main and test sources on compile
scalafmtOnCompile := true
scalafmtVersion := "1.4.0"

libraryDependencies ++= Seq(
  "com.hierynomus"   % "sshj"            % "0.23.0",
  "org.slf4j"        % "slf4j-api"       % "1.7.25",
  "org.bouncycastle" % "bcprov-jdk15on"  % "1.59" % "provided",
  "com.jcraft"       % "jzlib"           % "1.1.3" % "provided",
  "ch.qos.logback"   % "logback-classic" % "1.2.3" % "test",
  "org.scalatest"    %% "scalatest"      % "3.0.5" % "test"
)

///////////////
// publishing
///////////////

crossScalaVersions := Seq("2.10.6", "2.11.12", "2.12.4")
useGpg := true
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ ⇒
  false
}
publishTo := sonatypePublishTo.value
