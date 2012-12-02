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
``inputKey.apply`` factory method:

::

      // goes in <base>/project/Build.scala or in <base>/build.sbt
      val demo = inputKey[Unit]("A demo input task.")

The definition of an input task is similar to that of a normal task, but it can
also use the result of a `Parser </Detailed-Topics/Parsing-Input>`_ applied to
user input.  Just as the special ``value`` method gets the value of a
setting or task, the special ``parsed`` method gets the result of a ``Parser``.

Basic Input Task Definition
===========================

The simplest input task accepts a space-delimited sequence of arguments.
It does not provide useful tab completion and parsing is basic.  The built-in
parser for space-delimited arguments is constructed via the ``spaceDelimited``
method, which accepts as its only argument the label to present to the user
during tab completion.

For example, the following task prints the current Scala version and then echoes
the arguments passed to it on their own line.

::

    demo := {
        // get the result of parsing
      val args: Seq[String] = spaceDelimited("<arg>").parsed
        // Here, we also use the value of the `scalaVersion` setting
      println("The current Scala version is " + scalaVersion.value)
      println("The arguments to demo were:")
      args foreach println
    }

Input Task using Parsers
========================

The Parser provided by the ``spaceDelimited`` method does not provide
any flexibility in defining the input syntax.  , but using a custom parser
is just a matter of defining your own ``Parser`` as described on the
:doc:`/Detailed-Topics/Parsing-Input` page.

Constructing the Parser
-----------------------

The first step is to construct the actual ``Parser`` by defining a value
of one of the following types:

* ``Parser[I]``: a basic parser that does not use any settings
* ``Initialize[Parser[I]]``: a parser whose definition depends on one or more settings
* ``Initialize[State => Parser[I]]``: a parser that is defined using both settings and the current :doc:`state <Build-State>`

We already saw an example of the first case with ``spaceDelimited``, which doesn't use any settings in its definition.
As an example of the third case, the following defines a contrived ``Parser`` that uses the
project's Scala and sbt version settings as well as the state.  To use these settings, we
need to wrap the Parser construction in ``Def.setting`` and get the setting values with the
special ``value`` method:

::

      import complete.DefaultParsers._

    val parser: Initialize[State => Parser[(String,String)]] =
     Def.setting {
      (state: State) =>
        ( token("scala" <~ Space) ~ token(scalaVersion.value) ) |
        ( token("sbt" <~ Space) ~ token(sbtVersion.value) ) |
        ( token("commands" <~ Space) ~
            token(state.remainingCommands.size.toString) )
     }

This Parser definition will produce a value of type ``(String,String)``.
The input syntax defined isn't very flexible; it is just a demonstration. It
will produce one of the following values for a successful parse
(assuming the current Scala version is 2.10.0, the current sbt version is
0.13.0, and there are 3 commands left to run):

.. code-block:: text

    ("scala", "2.10.0")
    ("sbt", "0.13.0")
    ("commands", "3")

Again, we were able to access the current Scala and sbt version for the project because
they are settings.  Tasks cannot be used to define the parser.

Constructing the Task
---------------------

Next, we construct the actual task to execute from the result of the
``Parser``. For this, we define a task as usual, but we can access the
result of parsing via the special ``parsed`` method on ``Parser``.

The following contrived example uses the previous example's output (of
type ``(String,String)``) and the result of the ``package`` task to
print some information to the screen.

::

    demo := {
        val (tpe, value) = parser.parsed
        println("Type: " + tpe)
        println("Value: " + value)
        println("Packaged: " + packageBin.value.getAbsolutePath)
    }
