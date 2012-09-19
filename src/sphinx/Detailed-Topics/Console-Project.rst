===============
Console Project
===============

Description
===========

The ``console-project`` task starts the Scala interpreter with access to
your project definition and to ``sbt``. Specifically, the interpreter is
started up with these commands already executed:

::

    import sbt._
    import Process._
    import Keys._
    import <your-project-definition>._
    import currentState._
    import extracted._

For example, running external processes with sbt's process library (to
be included in the standard library in Scala 2.9):

.. code-block:: console

    > "tar -zcvf project-src.tar.gz src" !
    > "find project -name *.jar" !
    > "cat build.sbt" #| "grep version" #> new File("sbt-version") !
    > "grep -r null src" #|| "echo null-free" !
    > uri("http://databinder.net/dispatch/About").toURL #> file("About.html") !

``console-project`` can be useful for creating and modifying your build
in the same way that the Scala interpreter is normally used to explore
writing code. Note that this gives you raw access to your build. Think
about what you pass to ``IO.delete``, for example.

This task was especially useful in prior versions of sbt for showing the
value of settings. It is less useful for this now that
``show <setting>`` prints the result of a setting or task and ``set``
can define an anonymous task at the command line.

Accessing settings
==================

To get a particular setting, use the form:

.. code-block:: scala

    > val value = get(<key> in <scope>)

Examples
--------

.. code-block:: scala

    > IO.delete( get(classesDirectory in Compile) )

Show current compile options:

.. code-block:: scala

    > get(scalacOptions in Compile) foreach println

Show additionally configured repositories.

.. code-block:: scala

    > get( resolvers ) foreach println

Evaluating tasks
================

To evaluate a task, use the form:

.. code-block:: scala

    > val value = evalTask(<key> in <scope>, currentState)

Examples
--------

Show all repositories, including defaults.

.. code-block:: scala

    > evalTask( fullResolvers, currentState ) foreach println

Show the classpaths used for compilation and testing:

.. code-block:: scala

    > evalTask( fullClasspath in Compile, currentState ).files foreach println
    > evalTask( fullClasspath in Test, currentState ).files foreach println

Show the remaining commands to be executed in the build (more
interesting if you invoke ``console-project`` like
``; console-project ; clean ; compile``):

.. code-block:: scala

    > remainingCommands

Show the number of currently registered commands:

.. code-block:: scala

    > definedCommands.size
