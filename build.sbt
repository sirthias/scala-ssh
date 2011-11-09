name := "scala-ssh"

organization := "com.decodified"

version := "0.5.0-SNAPSHOT"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
	"net.schmizz" % "sshj" % "0.6.1",
	"org.slf4j" % "slf4j-api" % "1.6.1",
	"org.bouncycastle" % "bcprov-jdk16" % "1.46" % "provided",
	"com.jcraft" % "jzlib" % "1.0.7" % "provided",
	"org.specs2" %% "specs2" % "1.6.1" % "test",
	"ch.qos.logback" % "logback-classic" % "0.9.29" % "test"
)

resolvers ++= Seq(
    "Akka Repository" at "http://akka.io/repository/",
    "Jsch Reposiroty" at "http://jsch.sourceforge.net/maven2/"
)