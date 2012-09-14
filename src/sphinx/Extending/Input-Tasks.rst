===========
Input Tasks
===========

Input Tasks parse user input and produce a task to run.
:doc:`/Detailed-Topics/Parsing-Input/` describes how to use the parser
combinators that define the input syntax and tab completion. This page
describes how to hook those parser combinators into the input task
system.

Input Keys
==========

A key for an input task is of type ``InputKey`` and represents the input
task like a ``SettingKey`` represents a setting or a ``TaskKey``
represents a task. Define a new input task key using the
``InputKey.apply`` factory method:

::

      // goes in <base>/project/Build.scala
      val demo = InputKey[Unit]("demo")

Basic Input Task Definition
===========================

The simplest input task accepts a space-delimited sequence of arguments.
It does not provide useful tab completion and parsing is basic. Such a
task may be defined using the ``inputTask`` method, which accepts a
single function of type ``TaskKey[Seq[String]] => Initialize[Task[O]]``
for some parse result type ``O``. The input to this function is a
``TaskKey`` for a task that will provide the parsed ``Seq[String]``. The
function should return a task that uses that parsed input. For example:

::

    demo <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
        // Here, we map the argument task `argTask`
        // and a normal setting `scalaVersion`
      (argTask, scalaVersion) map { (args: Seq[String], sv: String) =>
        println("The current Scala version is " + sv)
        println("The arguments to demo were:")
        args foreach println
      }
    }

Input Task using Parsers
========================

The ``inputTask`` method does not provide any flexibility in defining
the input syntax. To use an arbitrary ``Parser`` described on the
:doc:`/Detailed-Topics/Parsing-Input` page for parsing your input
task's command line, use the more advanced
`InputTask.apply <../../api/sbt/InputTask$.html>`_ factory method. This
method accepts two arguments, which will be described in the following
two sections.

Constructing the Parser
-----------------------

The first step is to construct the actual ``Parser`` by defining a value
of type ``Initialize[State => Parser[I]]`` for some parse result type
``I`` that you decide on. ``Initialize`` is the type that results from
using other settings and the ``State => Parser[I]`` function provides
access to the :doc:`Build-State` when constructing the parser. As an
example, the following defines a contrived ``Parser`` that uses the
project's Scala and sbt version settings as well as the state.

::

      import complete.DefaultParsers._

    val parser: Initialize[State => Parser[(String,String)]] =
     (scalaVersion, sbtVersion) { (scalaV: String, sbtV: String) =>
      (state: State) =>
        ( token("scala" <~ Space) ~ token(scalaV) ) |
        ( token("sbt" <~ Space) ~ token(sbtV) ) |
        ( token("commands" <~ Space) ~
            token(state.remainingCommands.size.toString) )
    }

This Parser definition will produce a value of type ``(String,String)``.
The input syntax isn't very flexible; it is just a demonstration. It
will produce one of the following values for a successful parse
(assuming the current Scala version is 2.9.2, the current sbt version is
0.12.0, and there are 3 commands left to run):

::

    ("scala", "2.9.2")
    ("sbt", "0.12.0")
    ("commands", "3")

Constructing the Task
---------------------

Next, we construct the actual task to execute from the result of the
``Parser``. For this, we construct a value of type
``TaskKey[I] => Initialize[Task[O]]``, where ``I`` is the type returned
by the ``Parser`` we just defined and ``O`` is the type of the ``Task``
we will produce. The ``TaskKey[I]`` provides a task that will provide
the result of parsing.

The following contrived example uses the previous example's output (of
type ``(String,String)``) and the result of the ``package`` task to
print some information to the screen.

::

    val taskDef = (parsedTask: TaskKey[(String,String)]) => {
        // we are making a task, so use 'map'
      (parsedTask, packageBin) map { case ( (tpe: String, value: String), pkg: File) =>
        println("Type: " + tpe)
        println("Value: " + value)
        println("Packaged: " + pkg.getAbsolutePath)
      }
    }

Putting it together
-------------------

To construct the input task, combine the key, the parser, and the task
definition in a setting that goes in ``build.sbt`` or in the
``settings`` member of a ``Project`` in ``project/Build.scala``:

::

    demo <<= InputTask(parser)(taskDef)

