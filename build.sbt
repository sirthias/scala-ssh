import scalariform.formatter.preferences._

name := "scala-ssh"

version := "0.7.1"

organization := "com.decodified"

organizationHomepage := Some(new URL("http://decodified.com"))

description := "A Scala library providing remote shell access via SSH"

homepage := Some(new URL("https://github.com/sirthias/scala-ssh"))

startYear := Some(2011)

licenses := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))

scalaVersion := "2.11.6"

scalacOptions ++= Seq("-feature", "-language:implicitConversions", "-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "com.hierynomus" % "sshj" % "0.12.0",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.52" % "provided",
  "com.jcraft" % "jzlib" % "1.1.3" % "provided",
  "ch.qos.logback" % "logback-classic" % "1.1.2" % "test",
  "org.specs2" %% "specs2" % "2.4.6" % "test")

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

scalariformSettings(true)

scalariformPreferences := scalariformPreferences.value
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(DanglingCloseParenthesis, Preserve)

///////////////
// publishing
///////////////

crossScalaVersions := Seq("2.10.4", "2.11.6")

publishMavenStyle := true

useGpg := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

pomExtra :=
  <scm>
    <url>git@github.com:sirthias/scala-ssh.git</url>
    <connection>scm:git:git@github.com:sirthias/scala-ssh.git</connection>
  </scm>
  <developers>
    <developer>
      <id>sirthias</id>
      <name>Mathias Doenitz</name>
    </developer>
  </developers>
