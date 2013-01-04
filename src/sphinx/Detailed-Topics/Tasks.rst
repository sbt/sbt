=====
Tasks
=====

Tasks and settings are now introduced in the :doc:`getting started guide </Getting-Started/Basic-Def>`,
which you may wish to read first.  This older page has some additional detail.

*Wiki Maintenance Note:* This page should have its overlap with the
getting started guide cleaned up, and just have any advanced or
additional notes. It should maybe also be consolidated with
:doc:`TaskInputs`.

Introduction
============

sbt 0.10+ has a new task system that integrates with the new settings
system. Both settings and tasks produce values, but there are two major
differences between them:

1. Settings are evaluated at project load time. Tasks are executed on
   demand, often in response to a command from the user.
2. At the beginning of project loading, settings and their dependencies
   are fixed. Tasks can introduce new tasks during execution, however.
   (Tasks have flatMap, but Settings do not.)

Features
========

There are several features of the task system:

1. By integrating with the settings system, tasks can be added, removed,
   and modified as easily and flexibly as settings.
2. :doc:`Input Tasks <TaskInputs>`, the successor to method tasks, use
   :doc:`parser combinators <Parsing-Input>` to define the syntax for their
   arguments. This allows flexible syntax and tab-completions in the
   same way as :doc:`/Extending/Commands`.
3. Tasks produce values. Other tasks can access a task's value with the
   ``map`` and ``flatMap`` methods.
4. The ``flatMap`` method allows dynamically changing the structure of
   the task graph. Tasks can be injected into the execution graph based
   on the result of another task.
5. There are ways to handle task failure, similar to
   ``try/catch/finally``.
6. Each task has access to its own Logger that by default persists the
   logging for that task at a more verbose level than is initially
   printed to the screen.

These features are discussed in detail in the following sections. The
context for the code snippets will be either the body of a ``Build``
object in a :doc:`.scala file </Getting-Started/Full-Def>` or an expression
in a :doc:`build.sbt </Getting-Started/Basic-Def>`.

Defining a New Task
===================

Hello World example (sbt)
-------------------------

build.sbt

::


    TaskKey[Unit]("hello") := println("hello world!")

Hello World example (scala)
---------------------------

project/Build.scala

::


    import sbt._
    import Keys._

    object HelloBuild extends Build {
      val hwsettings = Defaults.defaultSettings ++ Seq(
        organization := "hello",
        name         := "world",
        version      := "1.0-SNAPSHOT",
        scalaVersion := "2.9.0-1"
      )

      val hello = TaskKey[Unit]("hello", "Prints 'Hello World'")

      val helloTask = hello := {
        println("Hello World")
      }

      lazy val project = Project (
        "project",
        file ("."),
        settings = hwsettings ++ Seq(helloTask)
      )
    }

Run "sbt hello" from command line to invoke the task. Run "sbt tasks" to
see this task listed.

Define the key
--------------

To declare a new task, define a ``TaskKey`` in your
:doc:`Full Configuration </Getting-Started/Full-Def>`:

::

    val sampleTask = TaskKey[Int]("sample-task")

The name of the ``val`` is used when referring to the task in Scala
code. The string passed to the ``TaskKey`` method is used at runtime,
such as at the command line. By convention, the Scala identifier is
camelCase and the runtime identifier uses hyphens. The type parameter
passed to ``TaskKey`` (here, ``Int``) is the type of value produced by
the task.

We'll define a couple of other of tasks for the examples:

::

    val intTask = TaskKey[Int]("int-task")
    val stringTask = TaskKey[String]("string-task")

The examples themselves are valid entries in a ``build.sbt`` or can be
provided as part of a sequence to ``Project.settings`` (see
:doc:`Full Configuration </Getting-Started/Full-Def>`).

Implement the task
------------------

There are three main parts to implementing a task once its key is
defined:

1. Determine the settings and other tasks needed by the task. They are
   the task's inputs.
2. Define a function that takes these inputs and produces a value.
3. Determine the scope the task will go in.

These parts are then combined like the parts of a setting are combined.

Tasks without inputs
~~~~~~~~~~~~~~~~~~~~

A task that takes no arguments can be defined using ``:=``

::

    intTask := 1 + 2

    stringTask := System.getProperty("user.name")

    sampleTask := {
       val sum = 1 + 2
       println("sum: " + sum)
       sum
    }

As mentioned in the introduction, a task is evaluated on demand.
Each time ``sample-task`` is invoked, for example, it will print the sum.
If the username changes between runs, ``string-task`` will take different values in those separate runs.
(Within a run, each task is evaluated at most once.)
In contrast, settings are evaluated once on project load and are fixed until the next reload.

Tasks with inputs
~~~~~~~~~~~~~~~~~

Tasks with other tasks or settings as inputs are defined using ``<<=``.
The right hand side will typically call ``map`` or ``flatMap`` on other
settings or tasks. (Contrast this with the ``apply`` method that is used
for settings.) The function argument to ``map`` or ``flatMap`` is the
task body. The following are equivalent ways of defining a task that
adds one to value produced by ``int-task`` and returns the result.

::

    sampleTask <<= intTask map { (count: Int) => count + 1 }

    sampleTask <<= intTask map { _ + 1 }

Multiple inputs are handled as with settings. The ``map`` and
``flatMap`` are done on a tuple of inputs:

::

    stringTask <<= (sampleTask, intTask) map { (sample: Int, intValue: Int) =>
        "Sample: " + sample + ", int: " + intValue
    }

Task Scope
~~~~~~~~~~

As with settings, tasks can be defined in a specific scope. For example,
there are separate ``compile`` tasks for the ``compile`` and ``test``
scopes. The scope of a task is defined the same as for a setting. In the
following example, ``test:sample-task`` uses the result of
``compile:int-task``.

::

    sampleTask.in(Test) <<= intTask.in(Compile).map { (intValue: Int) => 
        intValue * 3
    }

    // more succinctly:
    sampleTask in Test <<= intTask in Compile map { _ * 3 }

Inline task keys
~~~~~~~~~~~~~~~~

Although generally not recommended, it is possible to specify the task
key inline:

::

    TaskKey[Int]("sample-task") in Test <<= TaskKey[Int]("int-task") in Compile map { _ * 3 }

The type argument to ``TaskKey`` must be explicitly specified because of
``SI-4653``. It is not recommended because:

1. Tasks are no longer referenced by Scala identifiers (like
   ``sampleTask``), but by Strings (like ``"sample-task"``)
2. The type information must be repeated.
3. Keys should come with a description, which would need to be repeated
   as well.

On precedence
~~~~~~~~~~~~~

As a reminder, method precedence is by the name of the method.

1. Assignment methods have the lowest precedence. These are methods with
   names ending in ``=``, except for ``!=``, ``<=``, ``>=``, and names
   that start with ``=``.
2. Methods starting with a letter have the next highest precedence.
3. Methods with names that start with a symbol and aren't included in 1.
   have the highest precedence. (This category is divided further
   according to the specific character it starts with. See the Scala
   specification for details.)

Therefore, the second variant in the previous example is equivalent to
the following:

::

    (sampleTask in Test) <<= (intTask in Compile map { _ * 3 })

Modifying an Existing Task
==========================

The examples in this section use the following key definitions, which
would go in a ``Build`` object in a :doc:`Full Configuration </Getting-Started/Full-Def>`.
Alternatively, the keys may be specified inline, as discussed above.

::

    val unitTask = TaskKey[Unit]("unit-task")
    val intTask = TaskKey[Int]("int-task")
    val stringTask = TaskKey[String]("string-task")

The examples themselves are valid settings in a ``build.sbt`` file or as
part of a sequence provided to ``Project.settings``.

In the general case, modify a task by declaring the previous task as an
input.

::

    // initial definition
    intTask := 3

    // overriding definition that references the previous definition
    intTask <<= intTask map { (value: Int) => value + 1 }

Completely override a task by not declaring the previous task as an
input. Each of the definitions in the following example completely
overrides the previous one. That is, when ``int-task`` is run, it will
only print ``#3``.

::

    intTask := {
        println("#1")
        3
    }

    intTask := {
        println("#2")
        5
    }

    intTask <<= sampleTask map { (value: Int) => 
        println("#3")
        value - 3
    }

To apply a transformation to a single task, without using additional
tasks as inputs, use ``~=``. This accepts the function to apply to the
task's result:

::

    intTask := 3

    // increment the value returned by intTask
    intTask ~= { (x: Int) => x + 1 }

Task Operations
===============

The previous sections used the ``map`` method to define a task in terms
of the results of other tasks. This is the most common method, but there
are several others. The examples in this section use the task keys
defined in the previous section.

Dependencies
------------

To depend on the side effect of some tasks without using their values
and without doing additional work, use ``dependOn`` on a sequence of
tasks. The defining task key (the part on the left side of ``<<=``) must
be of type ``Unit``, since no value is returned.

::

    unitTask <<= Seq(stringTask, sampleTask).dependOn

To add dependencies to an existing task without using their values, call
``dependsOn`` on the task and provide the tasks to depend on. For
example, the second task definition here modifies the original to
require that ``string-task`` and ``sample-task`` run first:

::

    intTask := 4

    intTask <<= intTask.dependsOn(stringTask, sampleTask)

Streams: Per-task logging
-------------------------

New in sbt 0.10+ are per-task loggers, which are part of a more general
system for task-specific data called Streams. This allows controlling
the verbosity of stack traces and logging individually for tasks as well
as recalling the last logging for a task. Tasks also have access to
their own persisted binary or text data.

To use Streams, ``map`` or ``flatMap`` the ``streams`` task. This is a
special task that provides an instance of
`TaskStreams <../../api/sbt/std/TaskStreams.html>`_
for the defining task. This type provides access to named binary and
text streams, named loggers, and a default logger. The default
`Logger <../../api/sbt/Logger.html>`_,
which is the most commonly used aspect, is obtained by the ``log``
method:

::

    myTask <<= streams map { (s: TaskStreams) =>
      s.log.debug("Saying hi...")
      s.log.info("Hello!")
    }

You can scope logging settings by the specific task's scope:

::

    logLevel in myTask := Level.Debug

    traceLevel in myTask := 5

To obtain the last logging output from a task, use the ``last`` command:

.. code-block:: console

    $ last my-task
    [debug] Saying hi...
    [info] Hello!

The verbosity with which logging is persisted is controlled using the
``persist-log-level`` and ``persist-trace-level`` settings. The ``last``
command displays what was logged according to these levels. The levels
do not affect already logged information.

Handling Failure
----------------

This section discusses the ``andFinally``, ``mapFailure``, and ``mapR``
methods, which are used to handle failure of other tasks.

andFinally
~~~~~~~~~~

The ``andFinally`` method defines a new task that runs the original task
and evaluates a side effect regardless of whether the original task
succeeded. The result of the task is the result of the original task.
For example:

::

    intTask := error("I didn't succeed.")

    intTask <<= intTask andFinally { println("andFinally") }

This modifies the original ``intTask`` to always print "andFinally" even
if the task fails.

Note that ``andFinally`` constructs a new task. This means that the new
task has to be invoked in order for the extra block to run. This is
important when calling andFinally on another task instead of overriding
a task like in the previous example. For example, consider this code:

::

    intTask := error("I didn't succeed.")

    otherIntTask <<= intTask andFinally { println("andFinally") }

If ``int-task`` is run directly, ``other-int-task`` is never involved in
execution. This case is similar to the following plain Scala code:

::

    def intTask: Int =
      error("I didn't succeed.")

    def otherIntTask: Int =
      try { intTask }
      finally { println("finally") }

    intTask()

It is obvious here that calling intTask() will never result in "finally"
being printed.

mapFailure
~~~~~~~~~~

``mapFailure`` accepts a function of type ``Incomplete => T``, where
``T`` is a type parameter. In the case of multiple inputs, the function
has type ``Seq[Incomplete] => T``.
`Incomplete <../../api/sbt/Incomplete.html>`_
is an exception with information about any tasks that caused the failure
and any underlying exceptions thrown during task execution. The
resulting task defined by ``mapFailure`` fails if its input succeeds and
evaluates the provided function if it fails.

For example:

::

    intTask := error("Failed.")

    intTask <<= intTask mapFailure { (inc: Incomplete) => 
       println("Ignoring failure: " + inc)
       3
    }

This overrides the ``int-task`` so that the original exception is printed and the constant ``3`` is returned.

``mapFailure`` does not prevent other tasks that depend on the target
from failing. Consider the following example:

::

    intTask := if(shouldSucceed) 5 else error("Failed.")

    // return 3 if int-task fails. if it succeeds, this task will fail
    aTask <<= intTask mapFailure { (inc: Incomplete) => 3 }

    // a new task that increments the result of int-task
    bTask <<= intTask map { \_ + 1 }

    cTask <<= (aTask, bTask) map { (a,b) => a + b }

The following table lists the results of each task depending on the initially invoked task:

============== =============== ============= ============== ============== ==============
invoked task   int-task result a-task result  b-task result c-task result 	overall result
============== =============== ============= ============== ============== ==============
int-task       failure         not run       not run        not run        failure
a-task         failure         success       not run        not run        success
b-task         failure         not run       failure        not run        failure
c-task         failure         success       failure        failure        failure
int-task       success         not run       not run        not run        success
a-task         success         failure       not run        not run        failure
b-task         success         not run       success        not run        success
c-task         success         failure       success        failure        failure
============== =============== ============= ============== ============== ==============

The overall result is always the same as the root task (the directly
invoked task). A ``mapFailure`` turns a success into a failure, and a
failure into whatever the result of evaluating the supplied function is.
A ``map`` fails when the input fails and applies the supplied function
to a successfully completed input.

In the case of more than one input, ``mapFailure`` fails if all inputs
succeed. If at least one input fails, the supplied function is provided
with the list of ``Incomplete``\ s. For example:

::

    cTask <<= (aTask, bTask) mapFailure { (incs: Seq[Incomplete]) => 3 }

The following table lists the results of invoking ``c-task``, depending
on the success of ``aTask`` and ``bTask``:


=============  =============  =============
a-task result  b-task result  c-task result
=============  =============  =============
failure        failure        success
failure        success        success
success        failure        success
success        success        failure
=============  =============  =============

mapR
~~~~

``mapR`` accepts a function of type ``Result[S] => T``, where ``S`` is
the type of the task being mapped and ``T`` is a type parameter. In the
case of multiple inputs, the function has type
``(Result[A], Result[B], ...) => T``.
`Result <https://github.com/harrah/xsbt/latest/api/sbt/Result.html>`_
has the same structure as ``Either[Incomplete, S]`` for a task result of
type ``S``. That is, it has two subtypes:

-  ``Inc``, which wraps ``Incomplete`` in case of failure
-  ``Value``, which wraps a task's result in case of success.

Thus, ``mapR`` is always invoked whether or not the original task
succeeds or fails.

For example:

::

    intTask := error("Failed.")

    intTask <<= intTask mapR {
       case Inc(inc: Incomplete) =>
          println("Ignoring failure: " + inc)
          3
       case Value(v) =>
          println("Using successful result: " + v)
          v
    }

This overrides the original ``int-task`` definition so that if the original task fails, the exception is printed and the constant ``3`` is returned. If it succeeds, the value is printed and returned.
