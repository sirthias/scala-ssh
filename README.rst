**scala-ssh** is a Scala_ library providing remote shell access via SSH.
It builds on SSHJ_ to provide the following features:

* Remote execution of one or more shell commands
* Access to ``stdin``, ``stdout``, ``stderr`` and exitcode of remote shell commands
* Authentication via password or public key
* Host key verification via ``known_hosts`` file or explicit fingerprint
* Convenient configuration of remote host properties via config file, resource or directly in code
* Scala-idiomatic API


Installation
------------

The latest release is **0.6.4** and is built against Scala 2.9.3 as well as Scala 2.10.2.
It is available from http://repo.spray.io. If you use SBT_ you can pull in the *scala-ssh* artifacts with::

    resolvers += "spray repo" at "http://repo.spray.io"

    libraryDependencies += "com.decodified" %% "scala-ssh" % "0.6.4" cross CrossVersion.full

(the trailing "cross CrossVersion.full" modifier is only required for SBT_ 0.12.x)

SSHJ_ uses SLF4J_ for logging, so you might want to also add logback_ to your dependencies::

    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

Additionally, in many cases you will need the following two artifacts, which provide additional cypher and compression
support::

    libraryDependencies ++= Seq(
      "org.bouncycastle" % "bcprov-jdk16" % "1.46",
      "com.jcraft" % "jzlib" % "1.1.2"
    )


Usage
-----

The highest-level API element provided by *scala-ssh* is the ``SSH`` object. You use it like this::

    SSH("example.com") { client =>
      client.exec("ls -a").right.map { result =>
        println("Result:\n" + result.stdOutAsString())
      }
    }

This establishes an SSH connection to host ``example.com`` and gives you an ``SshClient`` instance that you can use
to execute one or more commands on the host.
``SSH.apply`` has a second (optional) parameter of type ``HostConfigProvider``, which is essentially a function
returning a ``HostConfig`` instance for a given hostname. A ``HostConfig`` looks like this::

    case class HostConfig(
      login: SshLogin,
      hostName: String = "",
      port: Int = 22,
      connectTimeout: Option[Int] = None,
      connectionTimeout: Option[Int] = None,
      commandTimeout: Option[Int] = None,
      enableCompression: Boolean = false,
      hostKeyVerifier: HostKeyVerifier = ...,
      sshjConfig: Config = ...
    )

It provides all the details required for properly establishing an SSH connection.
If you don't provide an explicit ``HostConfigProvider`` the default one will be used. For every hostname you pass to the
``SSH.apply`` method this default ``HostConfigProvider`` expects a file ``~/.scala-ssh/{hostname}``, which contains the
properties of a ``HostConfig`` in a simple config file format (see below for details). The ``HostResourceConfig`` object
gives you alternative ``HostConfigProvider`` implementations that read the host config from classpath resources.

If the file ``~/.scala-ssh/{hostname}`` (or the classpath resource ``{hostname}``) doesn't exist *scala-ssh* looks for
more general files (or resources) in the following way:

1. As long as the first segment of the host name (up to the first ``.``) contains one or more digits replace the
   rightmost of these with ``X`` and look for a respectively named file or resource. Repeat until no digits left.
2. Drop all characters up to (and including) the first ``.`` from the host name and look for a respectively named file
   or resource.
3. Repeat from 1. as long as there are characters left.

This means that for a host with name ``node42.tier1.example.com`` the following locations (either under
``~/.scala-ssh/`` or the classpath, depending on the ``HostConfigProvider``) are tried:

1. ``node42.tier1.example.com``
2. ``node4X.tier1.example.com``
3. ``nodeXX.tier1.example.com``
4. ``tier1.example.com``
5. ``tierX.example.com``
6. ``example.com``
7. ``com``


Host Config File Format
-----------------------

A host config file is a UTF8-encoded text file containing ``key = value`` pairs, one per line. Blank lines and lines
starting with a ``#`` character are ignored. This is an example file::

    # simple password-based config
    login-type = password
    username = bob
    password = 123
    command-timeout = 5000
    enable-compression = yes

These key are defined:

login-type
  required, can be either ``password`` or ``keyfile``

host-name
  optional, if not given the name of the config file is assumed to be the hostname

port
  optional, the default value is ``22``

username
  required

password
  required for login-type ``password``, ignored otherwise

keyfile
  optionally specifies the location of the user keyfile to use with login-type ``keyfile``,
  if not given the default files ``~/.ssh/id_rsa`` and ``~/.ssh/id_dsa`` are tried, ignored for login-type ``password``,
  if the filename starts with a ``+`` the file is searched in addition to the default locations, if the filename starts
  with ``classpath:`` it is interpreted as the name of a classpath resource holding the private key

passphrase
  optionally specifies the passphrase for the keyfile, if not given the keyfile is assumed to be unencrypted,
  ignored for login-type ``password``

connect-timeout
  optionally specifies the number of milli-seconds that a connection request has to succeed in before triggering a
  timeout error, default value is 'no timeout'

connection-timeout
  optionally specifies the number of milli-seconds that an idle connection is held open before being closed due due to
  idleness, default value is 'no timeout'

command-timeout
  optionally specifies the number of milli-seconds that a pending response to an issued command is waited for before
  triggering a timeout error, default value is 'no timeout'

enable-compression
  optionally adds ``zlib`` compression to preferred compression algorithms, there is no guarantee that it will be
  successfully negotiatied, requires ``jzlib`` on the classpath (see 'installation' chapter) above, default is 'no'

fingerprint
  optionally specifies the fingerprint of the public host key to verify in standard SSH format
  (e.g. ``4b:69:6c:72:6f:79:20:77:61:73:20:68:65:72:65:21``), if not given the standard ``~/.ssh/known_hosts`` or
  ``~/.ssh/known_hosts2`` files will be searched for a matching entry, fingerprint verification can be entirely disabled
  by setting ``fingerprint = any``


License
-------

*scala-ssh* is licensed under `APL 2.0`_.


Patch Policy
------------

Feedback and contributions to the project, no matter what kind, are always very welcome.
However, patches can only be accepted from their original author.
Along with any patches, please state that the patch is your original work and that you license the work to the
*scala-ssh* project under the project’s open source license.


.. _Scala: http://www.scala-lang.org/
.. _sshj: https://github.com/shikhar/sshj
.. _SBT: https://github.com/harrah/xsbt/wiki
.. _SLF4J: http://www.slf4j.org/
.. _logback: http://logback.qos.ch/
.. _APL 2.0: http://www.apache.org/licenses/LICENSE-2.0