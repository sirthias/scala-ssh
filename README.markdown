_scala-ssh_ is a [Scala] library providing remote shell access via SSH.
It builds on [sshj] to provide the following features:

* Remote execution of one or more shell commands
* Access to `stdin`, `stdout`, `stderr` and exitcode of remote shell commands
* Authentication via password or public key
* Host key verification via `known_hosts` file or explicit fingerprint
* Convenient configuration of remote host properties via config file, resource or directly in code
* Scala-idiomatic API


## Installation

The current release is *0.5.0*, it's available from <http://repo.spray.cc>.
If you use [SBT] you can pull in the _scala-ssh_ artifacts with:

    resolvers += "spray repo" at "http://repo.spray.cc"

    libraryDependencies += "com.decodified" %% "scala-ssh" % "0.5.0"

[sshj] uses [SLF4J] for logging, so you might want to also add [logback] to your dependencies:

    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.0"

Additionally, in many cases you will need the following two artifacts, which provide additional cypher and compression
support:

    libraryDependencies ++= Seq(
      "org.bouncycastle" % "bcprov-jdk16" % "1.46",
      "com.jcraft" % "jzlib" % "1.0.7"
    )


## Usage

The highest-level API element provided by _scala-ssh_ is the `SSH` object. You use it like this:

    SSH("example.com") { client =>
      client.exec("ls -a").right.map { result =>
        println("Result:\n" + result.stdOutAsString())
      }
    }

This establishes an SSH connection to host `example.com` and gives you an `SshClient` instance that you can use
to execute one or more commands on the host.
`SSH.apply` has a second (optional) parameter of type `HostConfigProvider`, which is essentially a function returning
a `HostConfig` instance for a given hostname. A `HostConfig` looks like this:

    case class HostConfig(
      login: SshLogin,
      hostName: String = "",
      port: Int = 22,
      connectTimeout: Option[Int] = None,
      connectionTimeout: Option[Int] = None,
      commandTimeout: Option[Int] = None,
      useCompression: Boolean = false,
      hostKeyVerifier: HostKeyVerifier = ...,
      sshjConfig: Config = ...
    )

It provides all the details required for properly establishing an SSH connection.
If you don't provide an explicit `HostConfigProvider` the default one will be used. For every hostname you pass to the
`SSH.apply` method this default `HostConfigProvider` expects a file `~/.scala-ssh/{hostname}`, which contains the
properties of a `HostConfig` in a simple config file format (see below for details). The `HostResourceConfig` object
gives you alternative `HostConfigProvider` implementations that read the host config from JAR resources.


## Host Config File Format

A host config file is a UTF8-encoded text file containing `key = value` pairs, one per line. Blank lines and lines
starting with a `#` character are ignored. This is an example file:

    # simple password-based config
    login-type = password
    username = bob
    password = 123
    command-timeout = 5000
    enable-compression = yes

These key are defined:

* `login-type`: required, can be either `password` or `keyfile`

* `host-name`: optional, if not given the name of the config file is assumed to be the hostname

* `port`: optional, the default value is `22`

* `username`: required

* `password`: required for login-type `password`, ignored otherwise

* `keyfile`: optionally specifies the location of the user keyfile to use with login-type `keyfile`,
  if not given the default files `~/.ssh/id_rsa` and `~/.ssh/id_dsa` are tried, ignored for login-type `password`

* `passphrase`: optionally specifies the passphrase for the keyfile, if not given the keyfile is assumed to be
  unencrypted, ignored for login-type `password`

* `connect-timeout`: optionally specifies the number of milli-seconds that a connection request has to succeed in
  before triggering a timeout error, default value is 'no timeout'

* `connection-timeout`: optionally specifies the number of milli-seconds that an idle connection is held open before
  being closed due due to idleness, default value is 'no timeout'

* `commandTimeout`: optionally specifies the number of milli-seconds that a pending response to an issued command
  is waited for before triggering a timeout error, default value is 'no timeout'

* `use-compression`: optionally adds `zlib` compression to preferred compression algorithms, there is no guarantee
  that it will be successfully negotiatied, requires `jzlib` on the classpath (see 'installation' chapter) above,
  default is 'no'


### License

_scala-ssh_ is licensed under [APL 2.0].


### Patch Policy

Feedback and contributions to the project, no matter what kind, are always very welcome.
However, patches can only be accepted from their original author.
Along with any patches, please state that the patch is your original work and that you license the work to the
_scala-ssh_ project under the project’s open source license.


  [Scala]: http://www.scala-lang.org/
  [sshj]: https://github.com/shikhar/sshj
  [SBT]: https://github.com/harrah/xsbt/wiki
  [SLF4J]: http://www.slf4j.org/
  [logback]: http://logback.qos.ch/
  [APL 2.0]: http://www.apache.org/licenses/LICENSE-2.0