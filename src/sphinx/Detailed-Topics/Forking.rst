=======
Forking
=======

By default, the ``run`` task runs in the same JVM as sbt. Forking is
required under :doc:`certain circumstances <Running-Project-Code>`, however.
Or, you might want to fork Java processes when implementing new tasks.

By default, a forked process uses the same Java and Scala versions being
used for the build and the working directory and JVM options of the
current process. This page discusses how to enable and configure forking
for both ``run`` and ``test`` tasks. Each kind of task may be configured
separately by scoping the relevant keys as explained below.

Enable forking
==============

The ``fork`` setting controls whether forking is enabled (true) or not
(false). It can be set in the ``run`` scope to only fork ``run``
commands or in the ``test`` scope to only fork ``test`` commands.

To fork all test tasks (``test``, ``test-only``, and ``test-quick``) and
run tasks (``run``, ``run-main``, ``test:run``, and ``test:run-main``),

::

    fork := true

To enable forking ``run`` tasks only, set ``fork`` to ``true`` in the
``run`` scope.

::

    fork in run := true

To only fork ``test:run`` and ``test:run-main``:

::

    fork in (Test,run) := true

Similarly, set ``fork in (Compile,run) := true`` to only fork the main
``run`` tasks. ``run`` and ``run-main`` share the same configuration and
cannot be configured separately.

To enable forking all ``test`` tasks only, set ``fork`` to ``true`` in
the ``test`` scope:

::

    fork in test := true

See :doc:`Testing` for more control over how tests are assigned to JVMs and
what options to pass to each group.

Change working directory
========================

To change the working directory when forked, set
``baseDirectory in run`` or ``baseDirectory in test``:

::

    // sets the working directory for all `run`-like tasks
    baseDirectory in run := file("/path/to/working/directory/")

    // sets the working directory for `run` and `run-main` only
    baseDirectory in (Compile,run) := file("/path/to/working/directory/")

    // sets the working directory for `test:run` and `test:run-main` only
    baseDirectory in (Test,run) := file("/path/to/working/directory/")

    // sets the working directory for `test`, `test-quick`, and `test-only`
    baseDirectory in test := file("/path/to/working/directory/")

Forked JVM options
==================

To specify options to be provided to the forked JVM, set
``javaOptions``:

::

    javaOptions in run += "-Xmx8G"

or specify the configuration to affect only the main or test ``run``
tasks:

::

    javaOptions in (Test,run) += "-Xmx8G"

or only affect the ``test`` tasks:

::

    javaOptions in test += "-Xmx8G"

Java Home
=========

Select the Java installation to use by setting the ``java-home``
directory:

::

    javaHome := file("/path/to/jre/")

Note that if this is set globally, it also sets the Java installation
used to compile Java sources. You can restrict it to running only by
setting it in the ``run`` scope:

::

    javaHome in run := file("/path/to/jre/")

As with the other settings, you can specify the configuration to affect
only the main or test ``run`` tasks or just the ``test`` tasks.

Configuring output
==================

By default, forked output is sent to the Logger, with standard output
logged at the ``Info`` level and standard error at the ``Error`` level.
This can be configured with the ``output-strategy`` setting, which is of
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
test ``run`` tasks or for ``test`` tasks.

Configuring Input
=================

By default, the standard input of the sbt process is not forwarded to
the forked process. To enable this, configure the ``connectInput``
setting:

::

    connectInput in run := true

Direct Usage
============

To fork a new Java process, use the `Fork
API <../../api/sbt/Fork$.html>`_. The
methods of interest are ``Fork.java``, ``Fork.javac``, ``Fork.scala``,
and ``Fork.scalac``. See the
`ForkJava <../../api/sbt/Fork$.ForkJava.html>`_
and
`ForkScala <../../api/sbt/Fork$.ForkScala.html>`_
classes for the arguments and types.
