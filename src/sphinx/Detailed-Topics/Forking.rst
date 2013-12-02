=======
Forking
=======

By default, the :key:`run` task runs in the same JVM as sbt. Forking is
required under :doc:`certain circumstances <Running-Project-Code>`, however.
Or, you might want to fork Java processes when implementing new tasks.

By default, a forked process uses the same Java and Scala versions being
used for the build and the working directory and JVM options of the
current process. This page discusses how to enable and configure forking
for both :key:`run` and :key:`test` tasks. Each kind of task may be configured
separately by scoping the relevant keys as explained below.

Enable forking
==============

The :key:`fork` setting controls whether forking is enabled (true) or not
(false). It can be set in the :key:`run` scope to only fork :key:`run`
commands or in the :key:`test` scope to only fork :key:`test` commands.

To fork all test tasks (:key:`test`, :key:`testOnly`, and :key:`testQuick`) and
run tasks (:key:`run`, :key:`runMain`, `test:run`, and `test:runMain`),

::

    fork := true

To enable forking :key:`run` tasks only, set :key:`fork` to `true` in the
:key:`run` scope.

::

    fork in run := true

To only fork `test:run` and `test:runMain`:

::

    fork in (Test,run) := true

Similarly, set `fork in (Compile,run) := true` to only fork the main
:key:`run` tasks. :key:`run` and :key:`runMain` share the same configuration and
cannot be configured separately.

To enable forking all :key:`test` tasks only, set :key:`fork` to `true` in
the :key:`test` scope:

::

    fork in test := true

See :doc:`Testing` for more control over how tests are assigned to JVMs and
what options to pass to each group.

Change working directory
========================

To change the working directory when forked, set
`baseDirectory in run` or `baseDirectory in test`:

::

    // sets the working directory for all `run`-like tasks
    baseDirectory in run := file("/path/to/working/directory/")

    // sets the working directory for `run` and `runMain` only
    baseDirectory in (Compile,run) := file("/path/to/working/directory/")

    // sets the working directory for `test:run` and `test:runMain` only
    baseDirectory in (Test,run) := file("/path/to/working/directory/")

    // sets the working directory for `test`, `testQuick`, and `testOnly`
    baseDirectory in test := file("/path/to/working/directory/")

Forked JVM options
==================

To specify options to be provided to the forked JVM, set
:key:`javaOptions`:

::

    javaOptions in run += "-Xmx8G"

or specify the configuration to affect only the main or test `run`
tasks:

::

    javaOptions in (Test,run) += "-Xmx8G"

or only affect the :key:`test` tasks:

::

    javaOptions in test += "-Xmx8G"

Java Home
=========

Select the Java installation to use by setting the :key:`javaHome`
directory:

::

    javaHome := file("/path/to/jre/")

Note that if this is set globally, it also sets the Java installation
used to compile Java sources. You can restrict it to running only by
setting it in the :key:`run` scope:

::

    javaHome in run := file("/path/to/jre/")

As with the other settings, you can specify the configuration to affect
only the main or test :key:`run` tasks or just the :key:`test` tasks.

Configuring output
==================

By default, forked output is sent to the Logger, with standard output
logged at the `Info` level and standard error at the `Error` level.
This can be configured with the :key:`outputStrategy` setting, which is of
type
`OutputStrategy <../../api/sbt/OutputStrategy.html>`_.

::

    // send output to the build's standard output and error
    outputStrategy := Some(StdoutOutput)

    // send output to the provided OutputStream `someStream`
    outputStrategy := Some(CustomOutput(someStream: OutputStream))

    // send output to the provided Logger `log` (unbuffered)
    outputStrategy := Some(LoggedOutput(log: Logger))

    // send output to the provided Logger `log` after the process terminates
    outputStrategy := Some(BufferedOutput(log: Logger))

As with other settings, this can be configured individually for main or
test :key:`run` tasks or for :key:`test` tasks.

Configuring Input
=================

By default, the standard input of the sbt process is not forwarded to
the forked process. To enable this, configure the :key:`connectInput`
setting:

::

    connectInput in run := true

Direct Usage
============

To fork a new Java process, use the `Fork
API <../../api/sbt/Fork$.html>`_.
The values of interest are `Fork.java`, `Fork.javac`, `Fork.scala`, and `Fork.scalac`.
These are of type `Fork <../../api/sbt/Fork.html>`_ and provide `apply` and `fork` methods.
For example, to fork a new Java process, ::

    val options = ForkOptions(...)
    val arguments: Seq[String] = ...
    val mainClass: String = ...
    val exitCode: Int = Fork.java(options, mainClass +: arguments)


`ForkOptions <../../api/sbt/ForkOptions.html>`_ defines the Java installation to use, the working directory, environment variables, and more.
For example, ::

    val cwd: File = ...
    val javaDir: File = ...
    val options = ForkOptions(
       envVars = Map("KEY" -> "value"),
       workingDirectory = Some(cwd),
       javaHome = Some(javaDir)
    )
