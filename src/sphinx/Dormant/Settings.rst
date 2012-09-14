*Wiki Maintenance Note:* This page has been partly replaced by [[Getting
Started Basic Def]] and [[Getting Started More About Settings]]. It has
some obsolete terminology:

-  we now avoid referring to build definition as "configuration" to
   avoid confusion with compile configurations
-  we now avoid referring to basic/light/quick vs. full configuration,
   in favor of ".sbt build definition files" and ".scala build
   definition files"

However, it may still be worth combing this page for examples or points
that are not made in new pages. We may want to add FAQs or topic pages
to supplement the Getting Started pages with some of that information.
After doing so, this page could simply be a redirect (delete the
content, link to the new pages about build definition).

Introduction
------------

A build definition is written in Scala. There are two types of
definitions: light and full. A :doc:`light definition <Basic-Configuration>`
is a quick way of configuring a build, consisting of a list of Scala
expressions describing project settings. A :doc:`full definition <Full-Configuration>` is
made up of one or more Scala source files that describe relationships
between projects and introduce new configurations and settings. This
page introduces the ``Setting`` type, which is used by light and full
definitions for general configuration.

Introductory Examples
~~~~~~~~~~~~~~~~~~~~~

Basic examples of each type of definition are shown below for the
purpose of getting an idea of what they look like, not for full
comprehension of details, which are described at :doc:`light definition <Basic-Configuration>`
and :doc:`full definition <Full-Configuration>`.

``<base>/build.sbt`` (light)

::

    name := "My Project"

    libraryDependencies += "junit" % "junit" % "4.8" % "test"

``<base>/project/Build.scala`` (full)

::

    import sbt._
    import Keys._

    object MyBuild extends Build
    {
       lazy val root = Project("root", file(".")) dependsOn(sub)
       lazy val sub = Project("sub", file("sub")) settings(
          name := "My Project",
          libraryDependencies += "junit" % "junit" % "4.8" % "test"
       )
    }

Important Settings Background
-----------------------------

The fundamental type of a configurable in sbt is a ``Setting[T]``. Each
line in the ``build.sbt`` example above is of this type. The arguments
to the ``settings`` method in the ``Build.scala`` example are of type
``Setting[T]``. Specifically, the ``name`` setting has type
``Setting[String]`` and the ``libraryDependencies`` setting has type
``Setting[Seq[ModuleID]]``, where ``ModuleID`` represents a dependency.

Throughout the documentation, many examples show a setting, such as:

::

    libraryDependencies += "junit" % "junit" % "4.8" % "test"

This setting expression either goes in a :doc:`light definition <Basic-Configuration>`
``(build.sbt)`` as is or in the ``settings`` of a ``Project`` instance
in a :doc:`full definition <Full-Configuration>`
``(Build.scala)`` as shown in the example. This is an important point to
understanding the context of examples in the documentation. (That is,
you now know where to copy and paste examples now.)

A ``Setting[T]`` describes how to initialize a setting of type ``T``.
The settings shown in the examples are expressions, not statements. In
particular, there is no hidden mutable map that is being modified. Each
``Setting[T]`` is a value that describes an update to a map. The actual
map is rarely directly referenced by user code. It is not the final map
that is usually important, but the operations on the map.

To emphasize this, the setting in the following ``Build.scala`` fragment
*is ignored* because it is a value that need to be included in the
``settings`` of a ``Project``. (Unfortunately, Scala will discard
non-Unit values to get Unit, which is why there is no compile error.)

::

    object Bad extends Build {
      libraryDependencies += "junit" % "junit" % "4.8" % "test"
    }

::

    object Good extends Build
    {
      lazy val root = Project("root", file(".")) settings(
        libraryDependencies += "junit" % "junit" % "4.8" % "test"
      )
    }

Declaring a Setting
-------------------

There is fundamentally one type of initialization, represented by the
``<<=`` method. The other initialization methods ``:=``, ``+=``,
``++=``, ``<+=``, ``<++=``, and ``~=`` are convenience methods that can
be defined in terms of ``<<=``.

The motivation behind the method names is:

-  All methods end with ``=`` to obtain the lowest possible infix
   precedence.
-  A method starting with ``<`` indicates that the initialization uses
   other settings.
-  A single ``+`` means a single value is expected and will be appended
   to the current sequence.
-  ``++`` means a ``Seq[T]`` is expected. The sequence will be appended
   to the current sequence.

The following sections include descriptions and examples of each
initialization method. The descriptions use "will initialize" or "will
append" to emphasize that they construct a value describing an update
and do not mutate anything. Each setting may be directly included in a
light configuration (build.sbt), appropriately separated by blank lines.
For a full configuration (Build.scala), the setting must go in a
settings Seq as described in the previous section. Information about the
types of the left and right hand sides of the methods follows this
section.

:=
~~

``:=`` is used to define a setting that overwrites any previous value
without referring to other settings. For example, the following defines
a setting that will set *name* to "My Project" regardless of whether
*name* has already been initialized.

::

    name := "My Project"

No other settings are used. The value assigned is just a constant.

+= and ++=
~~~~~~~~~~

``+=`` is used to define a setting that will append a single value to
the current sequence without referring to other settings. For example,
the following defines a setting that will append a JUnit dependency to
*libraryDependencies*. No other settings are referenced.

::

    libraryDependencies += "junit" % "junit" % "4.8" % "test"

The related method ``++=`` appends a sequence to the current sequence,
also without using other settings. For example, the following defines a
setting that will add dependencies on ScalaCheck and specs to the
current list of dependencies. Because it will append a ``Seq``, it uses
++= instead of +=.

::

    libraryDependencies ++= Seq(
       "org.scala-tools.testing" %% "scalacheck" % "1.9" % "test",
       "org.scala-tools.testing" %% "specs" % "1.6.8" % "test"
        )
    )

The types involved in += and ++= are constrained by the existence of an
implicit parameter of type Append.Value[A,B] in the case of += or
Append.Values[A,B] in the case of ++=. Here, B is the type of the value
being appended and A is the type of the setting that the value is being
appended to. See
`Append <../../api/sbt/Append$.html>`_
for the provided instances.

~=
~~

``~=`` is used to transform the current value of a setting. For example,
the following defines a setting that will remove ``-Y`` compiler options
from the current list of compiler options.

::

    scalacOptions in Compile ~= { (options: Seq[String]) =>
       options filterNot ( _ startsWith "-Y" )
    }

The earlier declaration of JUnit as a library dependency using ``+=``
could also be written as:

::

    libraryDependencies ~= { (deps: Seq[ModuleID]) =>
      deps :+ ("junit" % "junit" % "4.8" % "test")
    }

<<=
~~~

The most general method is <<=. All other methods can be implemented in
terms of <<=. <<= defines a setting using other settings, possibly
including the previous value of the setting being defined. For example,
declaring JUnit as a dependency using <<= would look like:

::

    libraryDependencies <<= libraryDependencies apply { (deps: Seq[ModuleID]) =>
       // Note that :+ is a method on Seq that appends a single value
       deps :+ ("junit" % "junit" % "4.8" % "test")
    }

This defines a setting that will apply the provided function to the
previous value of *libraryDependencies*. ``apply`` and ``Seq[ModuleID]``
are explicit for demonstration only and may be omitted.

<+= and <++=
~~~~~~~~~~~~

The <+= method is a hybrid of the += and <<= methods. Similarly, <++= is
a hybrid of the ++= and <<= methods. These methods are convenience
methods for using other settings to append to the current value of a
setting.

For example, the following will add a dependency on the Scala compiler
to the current list of dependencies. Because the *scalaVersion* setting
is used, the method is <+= instead of +=.

::

    libraryDependencies <+= scalaVersion( "org.scala-lang" % "scala-compiler" % _ )

This next example adds a dependency on the Scala compiler to the current
list of dependencies. Because another setting (*scalaVersion*) is used
and a Seq is appended, the method is <++=.

::

    libraryDependencies <++= scalaVersion { sv =>
      ("org.scala-lang" % "scala-compiler" % sv) ::
      ("org.scala-lang" % "scala-swing" % sv) ::
      Nil
    }

The types involved in <+= and <++=, like += and ++=, are constrained by
the existence of an implicit parameter of type Append.Value[A,B] in the
case of <+= or Append.Values[A,B] in the case of <++=. Here, B is the
type of the value being appended and A is the type of the setting that
the value is being appended to. See
`Append <../../api/sbt/Append$.html>`_
for the provided instances.

Setting types
-------------

This section provides information about the types of the left and
right-hand sides of the initialization methods. It is currently
incomplete.

Setting Keys
~~~~~~~~~~~~

The left hand side of a setting definition is of type
`ScopedSetting <../../api/sbt/ScopedSetting.html>`_.
This type has two parts: a key (of type
`SettingKey <../../api/sbt/SettingKey.html>`_)
and a scope (of type
`Scope <../../api/sbt/Scope$.html>`_). An
unspecified scope is like using ``this`` to refer to the current
context. The previous examples on this page have not defined an explicit
scope. See [[Inspecting Settings]] for details on the axes that make up
scopes.

The target (the value on the left) of a method like ``:=`` identifies
one of the main constructs in sbt: a setting, a task, or an input task.
It is not an actual setting or task, but a key representing a setting or
task. A setting is a value assigned when a project is loaded. A task is
a unit of work that is run on-demand after a project is loaded and
produces a value. An input task, previously known as a method task in
sbt 0.7 and earlier, accepts an input string and produces a task to be
run. (The renaming is because it can accept arbitrary input in 0.10+ and
not just a space-delimited sequence of arguments like in 0.7.)

A setting key has type
`SettingKey <../../api/sbt/SettingKey.html>`_,
a task key has type
`TaskKey <../../api/sbt/TaskKey.html>`_,
and an input task has type
`InputKey <../../api/sbt/InputKey.html>`_.
The remainder of this section only discusses settings. See [[Tasks]] and
[[Input Tasks]] for details on the other types (those pages assume an
understanding of this page).

To construct a
`ScopedSetting <../../api/sbt/ScopedSetting.html>`_,
select the key and then scope it using the ``in`` method (see the
`ScopedSetting <../../api/sbt/ScopedSetting.html>`_
for API details). For example, the setting for compiler options for the
test sources is referenced using the *scalacOptions* key and the
``Test`` configuration in the current project.

::

    val ref: ScopedSetting[Seq[String]] = scalacOptions in Test

The current project doesn't need to be explicitly specified, since that
is the default in most cases. Some settings are specific to a task, in
which case the task should be specified as part of the scope as well.
For example, the compiler options used for the *console* task for test
sources is referenced like:

::

    val ref: ScopedSetting[Seq[String]] = scalacOptions in Test in console

In these examples, the type of the setting reference key is given
explicitly and the key is assigned to a value to emphasize that it is a
normal (immutable) Scala value and can be manipulated and passed around
as such.

Computing the value for a setting
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The right hand side of a setting definition varies by the initialization
method used. In the case of :=, +=, ++=, and ~=, the type of the
argument is straightforward (see the
`ScopedSetting <../../api/sbt/ScopedSetting.html>`_
API). For <<=, <+=, and <++=, the type is ``Initialize[T]`` (for <<= and
<+=) or ``Initialize[Seq[T]]`` (for <++=). This section discusses the
`Initialize <../../api/sbt/Init$Initialize.html>`_
type.

A value of type ``Initialize[T]`` represents a computation that takes
the values of other settings as inputs. For example, in the following
setting, the argument to <<= is of type ``Initialize[File]``:

::

    scalaSource in Compile <<= baseDirectory {
       (base: File) => base / "src"
    }

This example can be written more explicitly as:

::

    {
      val key: ScopedSetting[File] = scalaSource.in(Compile)
      val init: Initialize[File] = baseDirectory.apply( (base: File) => base / "src" )
      key.<<=(init)
    }

To construct a value of type ``Initialize``, construct a tuple of up to
nine input ``ScopedSetting``\ s. Then, define the function that will
compute the value of the setting given the values for these input
settings.

::

    val path: Initialize[File] =
      (baseDirectory, name, version).apply( (base: File, n: String, v: String) =>
        base / (n + "-" + v + ".jar")
      )

This example takes the base directory, project name, and project version
as inputs. The keys for these settings are defined in [sbt.Keys], along
with all other built-in keys. The argument to the ``apply`` method is a
function that takes the values of those settings and computes a new
value. In this case, that value is the path of a jar.

Initialize[Task[T]]
~~~~~~~~~~~~~~~~~~~

To initialize tasks, the procedure is similar. There are a few
differences. First, the inputs are of type [ScopedTaskable]. The means
that either settings
(`ScopedSetting <../../api/sbt/ScopedSetting.html>`_)
or tasks ([ScopedTask]) may be used as the input to a task. Second, the
name of the method used is ``map`` instead of ``apply`` and the
resulting value is of type ``Initialize[Task[T]]``. In the following
example, the inputs are the [report\|Update-Report] produced by the
*update* task and the context *configuration*. The function computes the
locations of the dependencies for that configuration.

::

    val mainDeps: Initialize[Task[File]] =
      (update, configuration).map( (report: UpdateReport, config: Configuration) =>
        report.select(configuration = config.name)
      )

As before, *update* and *configuration* are defined in
`Keys <../../sxr/Keys.scala.html>`_.
*update* is of type ``TaskKey[UpdateReport]`` and *configuration* is of
type ``SettingKey[Configuration]``.
