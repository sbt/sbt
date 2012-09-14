*Wiki Maintenance Note:* This page has been replaced a couple of times;
first by
[`Settings <../../sxr/Settings.scala.html>`_\ ]
and most recently by [[Getting Started Basic Def]] and [[Getting Started
More About Settings]]. It has some obsolete terminology:

-  we now avoid referring to build definition as "configuration" to
   avoid confusion with compile configurations
-  we now avoid referring to basic/light/quick vs. full configuration,
   in favor of ".sbt build definition files" and ".scala build
   definition files"

However, it may still be worth combing this page for examples or points
that are not made in new pages. After doing so, this page could simply
be a redirect (delete the content, link to the new pages about build
definition).

Configuration
=============

A build definition is written in Scala. There are two types of
definitions: light and full. A light definition is a quick way of
configuring a build. It consists of a list of Scala expressions
describing project settings in one or more ".sbt" files located in the
base directory of the project. This also applies to sub-projects.

A full definition is made up of one or more Scala source files that
describe relationships between projects, introduce new configurations
and settings, and define more complex aspects of the build. The
capabilities of a light definition are a proper subset of those of a
full definition.

Light configuration and full configuration can co-exist. Settings
defined in the light configuration are appended to the settings defined
in the full configuration for the corresponding project.

Light Configuration
===================

By Example
----------

Create a file with extension ``.sbt`` in your root project directory
(such as ``<your-project>/build.sbt``). This file contains Scala
expressions of type ``Setting[T]`` that are separated by blank lines.
Built-in settings typically have reasonable defaults (an exception is
``publishTo``). A project typically redefines at least ``name`` and
``version`` and often ``libraryDependencies``. All built-in settings are
listed in
`Keys <../../sxr/Keys.scala.html>`_.

A sample ``build.sbt``:

::

    // Set the project name to the string 'My Project'
    name := "My Project"

    // The := method used in Name and Version is one of two fundamental methods.
    // The other method is <<=
    // All other initialization methods are implemented in terms of these.
    version := "1.0"

    // Add a single dependency
    libraryDependencies += "junit" % "junit" % "4.8" % "test"

    // Add multiple dependencies
    libraryDependencies ++= Seq(
        "net.databinder" %% "dispatch-google" % "0.7.8",
        "net.databinder" %% "dispatch-meetup" % "0.7.8" 
    )

    // Exclude backup files by default.  This uses ~=, which accepts a function of
    //  type T => T (here T = FileFilter) that is applied to the existing value.
    // A similar idea is overriding a member and applying a function to the super value:
    //  override lazy val defaultExcludes = f(super.defaultExcludes)
    //
    defaultExcludes ~= (filter => filter || "*~")
    /*  Some equivalent ways of writing this:
    defaultExcludes ~= (_ || "*~")
    defaultExcludes ~= ( (_: FileFilter) || "*~")
    defaultExcludes ~= ( (filter: FileFilter) => filter || "*~")
    */

    // Use the project version to determine the repository to publish to.
    publishTo <<= version { (v: String) =>
      if(v endsWith "-SNAPSHOT")
        Some(ScalaToolsSnapshots)
      else
        Some(ScalaToolsReleases)
    }

Notes
-----

-  Because everything is parsed as an expression, no semicolons are
   allowed at the ends of lines.
-  All initialization methods end with ``=`` so that they have the
   lowest possible precedence. Except when passing a function literal to
   ``~=``, you do not need to use parentheses for either side of the
   method. Ok:

``scala libraryDependencies += "junit" % "junit" % "4.8" % "test" libraryDependencies.+=("junit" % "junit" % "4.8" % "test") defaultExcludes ~= (_ || "*~") defaultExcludes ~= (filter => filter || "*~")``
Error:

\`\`\`console defaultExcludes ~= \_ \|\| "\*~"

error: missing parameter type for expanded function
((x\ :math:`1) => defaultExcludes.`\ colon$tilde(x\ :math:`1).`\ bar("*~"))
defaultExcludes ~= \_ \|\| "*\ ~" ^ error: value \| is not a member of
sbt.Project.Setting[sbt.FileFilter] defaultExcludes ~= \_ \|\| "*~" ^
\`\`\`* A block is an expression, with the last statement in the block
being the result. For example, the following is an expression:

``scala {     val x = 3     def y = 2     x + y }`` An example of using
a block to construct a Setting:

``scala version := {     // Define a regular expression to match the current branch     val current = """\*\s+(\w+)""".r     // Process the output of 'git branch' to get the current branch     val branch = "git branch --no-color".lines_!.collect { case current(name) => "-" + name }     // Append the current branch to the version.     "1.0" + branch.mkString }``
\* Remember that blank lines are used to clearly delineate expressions.
This happens before the expression is sent to the Scala compiler, so no
blank lines are allowed within a block.

More Information
----------------

-  A ``Setting[T]`` describes how to initialize a value of type T. The
   expressions shown in the example are expressions, not statements. In
   particular, there is no hidden mutable map that is being modified.
   Each ``Setting[T]`` describes an update to a map. The actual map is
   rarely directly referenced by user code. It is not the final map that
   is important, but the operations on the map.
-  There are fundamentally two types of initializations, ``:=`` and
   ``<<=``. The methods ``+=``, ``++=``, and ``~=`` are defined in terms
   of these. ``:=`` assigns a value, overwriting any existing value.
   ``<<=`` uses existing values to initialize a setting.
-  ``key ~= f`` is equivalent to ``key <<= key(f)``
-  ``key += value`` is equivalent to ``key ~= (_ :+ value)`` or
   ``key <<= key(_ :+ value)``
-  ``key ++= value`` is equivalent to ``key ~= (_ ++ value)`` or
   ``key <<= key(_ ++ value)``
-  There can be multiple ``.sbt`` files per project. This feature can be
   used, for example, to put user-specific configurations in a separate
   file.
-  Import clauses are allowed at the beginning of a ``.sbt`` file. Since
   they are clauses, no semicolons are allowed. They need not be
   separated by blank lines, but each import must be on one line. For
   example,

``scala import scala.xml.NodeSeq import math.{abs, pow}`` \* These
imports are defined by default in a ``.sbt`` file:

\`\`\`scala

import sbt.\_ import Process.\_ import Keys.\_
\`\`\ ``In addition, the contents of all public``\ Build\ ``and``\ Plugin\`
objects from the full definition are imported.

sbt uses the blank lines to separate the expressions and then it sends
them off to the Scala compiler. Each expression is parsed, compiled, and
loaded independently. The settings are combined into a
``Seq[Setting[_]]`` and passed to the settings engine. The engine groups
the settings by key, preserving order per key though, and then computes
the order in which each setting needs to be evaluated. Cycles and
references to uninitialized settings are detected here and dead settings
are dropped. Finally, the settings are transformed into a function that
is applied to an initially empty map.

Because the expressions can be separated before the compiler, sbt only
needs to recompile expressions that change. So, the work to respond to
changes is proportional to the number of settings that changed and not
the number of settings defined in the build. If imports change, all
expression in the ``.sbt`` file need to be recompiled.

Implementation Details (even more information)
----------------------------------------------

Each expression describes an initialization operation. The simplest
operation is context-free assignment using ``:=``. That is, no outside
information is used to determine the setting value. Operations other
than ``:=`` are implemented in terms of ``<<=``. The ``<<=`` method
specifies an operation that requires other settings to be initialized
and uses their values to define a new setting.

The target (left side value) of a method like ``:=`` identifies one of
the constructs in sbt: settings, tasks, and input tasks. It is not an
actual setting or task, but a key representing a setting or task. A
setting is a value assigned when a project is loaded. A task is a unit
of work that is run on-demand zero or more times after a project is
loaded and also produces a value. An input task, previously known as a
Method Task in 0.7 and earlier, accepts an input string and produces a
task to be run. The renaming is because it can accept arbitrary input in
0.10 and not just a space-delimited sequence of arguments like in 0.7.

A construct (setting, task, or input task) is identified by a scoped
key, which is a pair ``(Scope, AttributeKey[T])``. An ``AttributeKey``
associates a name with a type and is a typesafe key for use in an
``AttributeMap``. Attributes are best illustrated by the ``get`` and
``put`` methods on ``AttributeMap``:

::

    def get[T](key: AttributeKey[T]): Option[T]
    def put[T](key: AttributeKey[T], value: T): AttributeMap

For example, given a value ``k: AttributeKey[String]`` and a value
``m: AttributeMap``, ``m.get(k)`` has type ``Option[String]``.

In sbt, a Scope is mainly defined by a project reference and a
configuration (such as 'test' or 'compile'). Project data is stored in a
Map[Scope, AttributeMap]. Each Scope identifies a map. You can sort of
compare a Scope to a reference to an object and an AttributeMap to the
object's data.

In order to provide appropriate convenience methods for constructing an
initialization operation for each construct, an AttributeKey is
constructed through either a SettingKey, TaskKey, or InputKey:

::

    // underlying key: AttributeKey[String]
    val name = SettingKey[String]("name")

    // underlying key: AttributeKey[Task[String]]
    val hello = TaskKey[String]("hello")

    // underlying key: AttributeKey[InputTask[String]]
    val helloArgs = InputKey[String]("hello-with-args")

In the basic expression ``name := "asdf"``, the ``:=`` method is
implicitly available for a ``SettingKey`` and accepts an argument that
conforms to the type parameter of name, which is String.

The high-level API for constructing settings is defined in
`Scoped <../../api/sbt/Scoped$.html>`_. Scopes are defined in `Scope <../../api/sbt/Scope$.html>`_.
The underlying engine is in `Settings <../../sxr/Settings.scala.html>`_
and the heterogeneous map is in `Attributes <../../sxr/Attributes.scala.html>`_.

Built-in keys are in `Keys <../../sxr/Keys.scala.html>`_ and
default settings are defined in `Defaults <../../sxr/Defaults.scala.html>`_.
