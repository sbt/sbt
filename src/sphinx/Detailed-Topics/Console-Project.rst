===============
Console Project
===============

Description
===========

The :key:`consoleProject` task starts the Scala interpreter with access to
your project definition and to `sbt`. Specifically, the interpreter is
started up with these commands already executed:

::

    import sbt._
    import Process._
    import Keys._
    import <your-project-definition>._
    import currentState._
    import extracted._
    import cpHelpers._

For example, running external processes with sbt's process library (to
be included in the standard library in Scala 2.9):

.. code-block:: console

    > "tar -zcvf project-src.tar.gz src" !
    > "find project -name *.jar" !
    > "cat build.sbt" #| "grep version" #> new File("sbt-version") !
    > "grep -r null src" #|| "echo null-free" !
    > uri("http://databinder.net/dispatch/About").toURL #> file("About.html") !

:key:`consoleProject` can be useful for creating and modifying your build
in the same way that the Scala interpreter is normally used to explore
writing code. Note that this gives you raw access to your build. Think
about what you pass to `IO.delete`, for example.

Accessing settings
==================

To get a particular setting, use the form:

.. code-block:: scala

    > val value = (<key> in <scope>).eval

Examples
--------

.. code-block:: scala

    > IO.delete( (classesDirectory in Compile).eval )

Show current compile options:

.. code-block:: scala

    > (scalacOptions in Compile).eval foreach println

Show additionally configured repositories.

.. code-block:: scala

    > resolvers.eval foreach println

Evaluating tasks
================

To evaluate a task (and its dependencies), use the same form:

.. code-block:: scala

    > val value = (<key> in <scope>).eval

Examples
--------

Show all repositories, including defaults.

.. code-block:: scala

    > fullResolvers.eval foreach println

Show the classpaths used for compilation and testing:

.. code-block:: scala

    > (fullClasspath in Compile).eval.files foreach println
    > (fullClasspath in Test).eval.files foreach println

State
=====

The current :doc:`build State </Extending/Build-State>` is available as `currentState`.
The contents of `currentState` are imported by default and can be used without qualification.

Examples
--------

Show the remaining commands to be executed in the build (more
interesting if you invoke :key:`consoleProject` like
`; consoleProject ; clean ; compile`):

.. code-block:: scala

    > remainingCommands

Show the number of currently registered commands:

.. code-block:: scala

    > definedCommands.size
