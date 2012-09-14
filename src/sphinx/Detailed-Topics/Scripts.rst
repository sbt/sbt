===============================
Scripts, REPL, and Dependencies
===============================

sbt has two alternative entry points that may be used to:

-  Compile and execute a Scala script containing dependency declarations
   or other sbt settings
-  Start up the Scala REPL, defining the dependencies that should be on
   the classpath

These entry points should be considered experimental. A notable
disadvantage of these approaches is the startup time involved.

Setup
=====

To set up these entry points, you can either use
`conscript <https://github.com/n8han/conscript>`_ or manually construct
the startup scripts. In addition, there is a `setup
script <https://github.com/paulp/xsbtscript>`_ for the script mode that
only requires a JRE installed.

Setup with Conscript
--------------------

Install `conscript <https://github.com/n8han/conscript>`_.

::

    cs harrah/xsbt --branch 0.12.0

This will create two scripts: ``screpl`` and ``scalas``.

Manual Setup
------------

Duplicate your standard ``sbt`` script, which was set up according to
:doc:`Setup </Getting-Started/Setup>`, as ``scalas`` and ``screpl`` (or
whatever names you like).

``scalas`` is the script runner and should use ``sbt.ConsoleMain`` as
the main class, by adding the ``-Dsbt.main.class=sbt.ScriptMain``
parameter to the ``java`` command. Its command line should look like:

::

    java -Dsbt.main.class=sbt.ScriptMain -Dsbt.boot.directory=/home/user/.sbt/boot -jar sbt-launch.jar "$@"

For the REPL runner ``screpl``, use ``sbt.ConsoleMain`` as the main
class:

::

    java -Dsbt.main.class=sbt.ConsoleMain -Dsbt.boot.directory=/home/user/.sbt/boot -jar sbt-launch.jar "$@"

In each case, ``/home/user/.sbt/boot`` should be replaced with wherever
you want sbt's boot directory to be; you might also need to give more
memory to the JVM via ``-Xms512M -Xmx1536M`` or similar options, just
like shown in :doc:`Setup </Getting-Started/Setup>`.

Usage
=====

sbt Script runner
-----------------

The script runner can run a standard Scala script, but with the
additional ability to configure sbt. sbt settings may be embedded in the
script in a comment block that opens with ``/***``.

Example
~~~~~~~

Copy the following script and make it executable. You may need to adjust
the first line depending on your script name and operating system. When
run, the example should retrieve Scala, the required dependencies,
compile the script, and run it directly. For example, if you name it
``dispatch_example.scala``, you would do on Unix:

::

    chmod u+x dispatch_example.scala
    ./dispatch_example.scala

::

    #!/usr/bin/env scalas
    !#

    /***
    scalaVersion := "2.9.0-1"

    libraryDependencies ++= Seq(
      "net.databinder" %% "dispatch-twitter" % "0.8.3",
      "net.databinder" %% "dispatch-http" % "0.8.3"
    )
    */

    import dispatch.{ json, Http, Request }
    import dispatch.twitter.Search
    import json.{ Js, JsObject }

    def process(param: JsObject) = {
      val Search.text(txt)        = param
      val Search.from_user(usr)   = param
      val Search.created_at(time) = param

      "(" + time + ")" + usr + ": " + txt
    }

    Http.x((Search("#scala") lang "en") ~> (_ map process foreach println))

sbt REPL with dependencies
--------------------------

The arguments to the REPL mode configure the dependencies to use when
starting up the REPL. An argument may be either a jar to include on the
classpath, a dependency definition to retrieve and put on the classpath,
or a resolver to use when retrieving dependencies.

A dependency definition looks like:

::

    organization%module%revision

Or, for a cross-built dependency:

::

    organization%%module%revision

A repository argument looks like:

::

    "id at url"

Example:
~~~~~~~~

To add the Sonatype snapshots repository and add Scalaz 7.0-SNAPSHOT to
REPL classpath:

::

    screpl "sonatype-releases at https://oss.sonatype.org/content/repositories/snapshots/" "org.scalaz%%scalaz-core%7.0-SNAPSHOT"

This syntax was a quick hack. Feel free to improve it. The relevant
class is
`IvyConsole <../../sxr/IvyConsole.scala.html>`_.
