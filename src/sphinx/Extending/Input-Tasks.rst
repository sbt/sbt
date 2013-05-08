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

      // goes in project/Build.scala or in build.sbt
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
any flexibility in defining the input syntax.  Using a custom parser
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

The InputTask type
==================

It helps to look at the ``InputTask`` type to understand more advanced usage of input tasks.
The core input task type is:

::

    class InputTask[T](val parser: State => Parser[Task[T]])

Normally, an input task is assigned to a setting and you work with ``Initialize[InputTask[T]]``.

Breaking this down,

  1. You can use other settings (via ``Initialize``) to construct an input task.
  2. You can use the current ``State`` to construct the parser.
  3. The parser accepts user input and provides tab completion.
  4. The parser produces the task to run.

So, you can use settings or ``State`` to construct the parser that defines an input task's command line syntax.
This was described in the previous section.
You can then use settings, ``State``, or user input to construct the task to run.
This is implicit in the input task syntax.



Using other input tasks
=======================

The types involved in an input task are composable, so it is possible to reuse input tasks.
The ``.parsed`` and ``.evaluated`` methods are defined on InputTasks to make this more convenient in common situations:

 * Call ``.parsed`` on an ``InputTask[T]`` or ``Initialize[InputTask[T]]`` to get the ``Task[T]`` created after parsing the command line
 * Call ``.evaluated`` on an ``InputTask[T]`` or ``Initialize[InputTask[T]]`` to get the value of type ``T`` from evaluating that task 

In both situations, the underlying ``Parser`` is sequenced with other parsers in the input task definition.
In the case of ``.evaluated``, the generated task is evaluated.

The following example applies the ``run`` input task, a literal separator parser ``--``, and ``run`` again.
The parsers are sequenced in order of syntactic appearance,
so that the arguments before ``--`` are passed to the first ``run`` and the ones after are passed to the second.

::

    val run2 = inputKey[Unit](
	    "Runs the main class twice with different argument lists separated by --")
    
    val separator: Parser[String] = "--"
    
    run2 := {
       val one = (run in Compile).evaluated
       val sep = separator.parsed
       val two = (run in Compile).evaluated
    }

For a main class Demo that echoes its arguments, this looks like:

::

    $ sbt
    > run2 a b -- c d
    [info] Running Demo c d
    [info] Running Demo a b
    c
    d
    a
    b


Preapplying input
=================

Because ``InputTasks`` are built from ``Parsers``, it is possible to generate a new ``InputTask`` by applying some input programmatically.
Two convenience methods are provided on ``InputTask[T]`` and ``Initialize[InputTask[T]]`` that accept the String to apply.

 * ``partialInput`` applies the input and allows further input, such as from the command line
 * ``fullInput`` applies the input and terminates parsing, so that further input is not accepted

In each case, the input is applied to the input task's parser.
Because input tasks handle all input after the task name, they usually require initial whitespace to be provided in the input.

Consider the example in the previous section.
We can modify it so that we:

 * Explicitly specify all of the arguments to the first ``run``.  We use ``name`` and ``version`` to show that settings can be used to define and modify parsers.
 * Define the initial arguments passed to the second ``run``, but allow further input on the command line.

NOTE: the current implementation of ``:=`` doesn't actually support applying input derived from settings yet.

::

    val run2 = inputKey[Unit]("Runs the main class twice: " +
       "once with the project name and version as arguments"
       "and once with command line arguments preceded by hard coded values.")

    // The argument string for the first run task is ' <name> <version>'
    val firstInput: Initialize[String] =
       Def.setting(s" ${name.value} ${version.value}")

    // Make the first arguments to the second run task ' red blue'
    val secondInput: String = " red blue"

    val separator: Parser[String] = "--"

    run2 := {
       val one = (run in Compile).fullInput(firstInput.value).evaluated
       val two = (run in Compile).partialInput(secondInput).evaluated
    }

For a main class Demo that echoes its arguments, this looks like:

::

    $ sbt
    > run2 green
    [info] Running Demo demo 1.0
    [info] Running Demo red blue green
    demo
    1.0
    red
    blue
    green
