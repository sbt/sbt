============================================
Creating Command Line Applications Using sbt
============================================

There are several components of sbt that may be used to create a command
line application. The :doc:`launcher </Detailed-Topics/Launcher>` and
the :doc:`command system <Commands>` are the two main ones illustrated
here.

As described on the :doc:`launcher page </Detailed-Topics/Launcher>`, a launched
application implements the xsbti.AppMain interface and defines a brief
configuration file that users pass to the launcher to run the
application. To use the command system, an application sets up a
:doc:`State <Build-State>` instance that provides
:doc:`command implementations <Commands>` and the initial commands to
run. A minimal hello world example is given below.

Hello World Example
===================

There are three files in this example:

1. build.sbt
2. Main.scala
3. hello.build.properties

To try out this example:

1. Put the first two files in a new directory
2. Run ``sbt publish-local`` in that directory
3. Run ``sbt @path/to/hello.build.properties`` to run the application.

Like for sbt itself, you can specify commands from the command line
(batch mode) or run them at an prompt (interactive mode).

Build Definition: build.sbt
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The build.sbt file should define the standard settings: name, version,
and organization. To use the sbt command system, a dependency on the
``command`` module is needed. To use the task system, add a dependency
on the ``task-system`` module as well.

::

    organization := "org.example"

    name := "hello"

    version := "0.1-SNAPSHOT"

    libraryDependencies += "org.scala-sbt" % "command" % "0.12.0"

Application: Main.scala
~~~~~~~~~~~~~~~~~~~~~~~

The application itself is defined by implementing `xsbti.AppMain <../../api/xsbti/AppMain.html>`_. The basic steps are

1. Provide command definitions.  These are the commands that are available for users to run.
2. Define initial commands.  These are the commands that are initially scheduled to run.  For example, an application will typically add anything specified on the command line (what sbt calls batch mode) and if no commands are defined, enter interactive mode by running the 'shell' command.
3. Set up logging.  The default setup in the example rotates the log file after each user interaction and sends brief logging to the console and verbose logging to the log file.

::

    package org.example

       import sbt._
       import java.io.{File, PrintWriter}

    final class Main extends xsbti.AppMain
    {
       /** Defines the entry point for the application.
       * The call to `initialState` sets up the application.
       * The call to runLogged starts command processing. */
       def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
          MainLoop.runLogged( initialState(configuration) )

       /** Sets up the application by constructing an initial State instance with the supported commands
       * and initial commands to run.  See the State API documentation for details. */
       def initialState(configuration: xsbti.AppConfiguration): State =
       {
          val commandDefinitions = hello +: BasicCommands.allBasicCommands
          val commandsToRun = Hello +: "iflast shell" +: configuration.arguments.map(_.trim)
          State( configuration, commandDefinitions, Set.empty, None, commandsToRun, State.newHistory,
             AttributeMap.empty, initialGlobalLogging, State.Continue )
       }

       // defines an example command.  see the Commands page for details.
       val Hello = "hello"
       val hello = Command.command(Hello) { s =>
          s.log.info("Hello!")
          s
       }
            
       /** Configures logging to log to a temporary backing file as well as to the console. 
       * An application would need to do more here to customize the logging level and
       * provide access to the backing file (like sbt's last command and logLevel setting).*/
       def initialGlobalLogging: GlobalLogging =
          GlobalLogging.initial(MainLogging.globalDefault _, File.createTempFile("hello", "log"))
    }

Launcher configuration file: hello.build.properties
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The launcher needs a configuration file in order to retrieve and run an
application.

``hello.build.properties``

::

    [scala]
      version: 2.9.1

    [app]
      org: org.example
      name: hello
      version: 0.1-SNAPSHOT
      class: org.example.Main
      components: xsbti
      cross-versioned: true

    [repositories]
      local
      maven-central
      typesafe-ivy-releases: http://repo.typesafe.com/typesafe/ivy-releases/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]

