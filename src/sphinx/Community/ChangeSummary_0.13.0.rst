==============
0.13.0 Changes
==============

Overview
========

Features, fixes, changes with compatibility implications (incomplete, please help)
----------------------------------------------------------------------------------


- Moved to Scala 2.10 for sbt and build definitions.
- Support for plugin configuration in ``project/plugins/`` has been removed.  It was deprecated since 0.11.2.
- Dropped support for tab completing the right side of a setting for the ``set`` command.  The new task macros make this tab completion obsolete.
- The convention for keys is now camelCase only.  Details below.
- sbt no longer looks for main artifacts for poms with ``packaging="pom"``.  For details, see the :ref:`relevant Library Management section <packaging-pom>` and gh-636.

Features
--------

- Use the repositories in boot.properties as the default project resolvers.  Add ``bootOnly`` to a repository in boot.properties to specify that it should not be used by projects by default.  (Josh S., gh-608)
- Support vals and defs in .sbt files.  Details below.
- Support defining Projects in .sbt files: vals of type Project are added to the Build.  Details below.
- New syntax for settings, tasks, and input tasks.  Details below.
- Automatically link to external API scaladocs of dependencies by setting ``autoAPIMappings := true``.  This requires at least Scala 2.10.1 and for dependencies to define ``apiURL`` for their scaladoc location.  Mappings may be manually added to the ``apiMappings`` task as well.
- Support setting Scala home directory temporary using the switch command: ``++ /path/to/scala/home``.

Fixes
-----


Improvements
------------

- Run the API extraction phase after the compiler's ``pickler`` phase instead of ``typer`` to allow compiler plugins after ``typer``.
- Record defining source position of settings.  ``inspect`` shows the definition location of all settings contributing to a defined value.
- Allow the root project to be specified explicitly in ``Build.rootProject``.
- Tasks that need a directory for storing cache information can now use the ``cacheDirectory`` method on ``streams``.  This supersedes the ``cacheDirectory`` setting.

Other
-----

- The source layout for the sbt project itself follows the package name to accommodate to Eclipse users. (Grzegorz K., gh-613)

Details of major changes
========================

camelCase Key names
-------------------

The convention for key names is now camelCase only instead of camelCase for Scala identifiers and hyphenated, lower-case on the command line.  camelCase is accepted for existing hyphenated key names and the hyphenated form will still be accepted on the command line for those existing tasks and settings declared with hyphenated names.  Only camelCase will be shown for tab completion, however.

New key definition methods
--------------------------

There are new methods that help avoid duplicating key names by declaring keys as:

::

    val myTask = taskKey[Int]("A (required) description of myTask.")

The name will be picked up from the val identifier by the implementation of the taskKey macro so there is no reflection needed or runtime overhead.  Note that a description is mandatory and the method ``taskKey`` begins with a lowercase ``t``.  Similar methods exist for keys for settings and input tasks: ``settingKey`` and ``inputKey``.

New task/setting syntax
-----------------------

First, the old syntax is still supported with the intention of allowing conversion to the new syntax at your leisure.  There may be some incompatibilities and some may be unavoidable, but please report any issues you have with an existing build.

The new syntax is implemented by making ``:=``, ``+=``, and ``++=`` macros and making these the only required assignment methods.  To refer to the value of other settings or tasks, use the ``value`` method on settings and tasks.  This method is a stub that is removed at compile time by the macro, which will translate the implementation of the task/setting to the old syntax.

For example, the following declares a dependency on ``scala-reflect`` using the value of the ``scalaVersion`` setting:

::

   libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

The ``value`` method is only allowed within a call to ``:=``, ``+=``, or ``++=``.  To construct a setting or task outside of these methods, use ``Def.task`` or ``Def.setting``.  For example,

::

    val reflectDep = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

    libraryDependencies += reflectDep.value   

A similar method ``parsed`` is defined on ``Parser[T]``, ``Initialize[Parser[T]]`` (a setting that provides a parser), and ``Initialize[State => Parser[T]]`` (a setting that uses the current ``State`` to provide a ``Parser[T]``.  This method can be used when defining an input task to get the result of user input.  

::

    myInputTask := {
         // Define the parser, which is the standard space-delimited arguments parser.
       val args = Def.spaceDelimited("<args>").parsed
         // Demonstrates using a setting value and a task result:
       println("Project name: " + name.value)
       println("Classpath: " + (fullClasspath in Compile).value.map(_.file))
       println("Arguments:")
       for(arg <- args) println("  " + arg)
    }

To expect a task to fail and get the failing exception, use the ``failure`` method instead of ``value``.  This provides an ``Incomplete`` value, which wraps the exception.  To get the result of a task whether or not it succeeds, use ``result``, which provides a ``Result[T]``.

Dynamic settings and tasks (``flatMap``) have been cleaned up.  Use the ``Def.taskDyn`` and ``Def.settingDyn`` methods to define them (better name suggestions welcome).  These methods expect the result to be a task and setting, respectively.

.sbt format enhancements
------------------------

vals and defs are now allowed in .sbt files.  They must follow the same rules as settings concerning blank lines, although multiple definitions may be grouped together.  For example,

::

    val n = "widgets"
    val o = "org.example"

    name := n

    organization := o

All definitions are compiled before settings, but it will probably be best practice to put definitions together.
Currently, the visibility of definitions is restricted to the .sbt file it is defined in.
They are not visible in ``consoleProject`` or the ``set`` command at this time, either.
Use Scala files in ``project/`` for visibility in all .sbt files.

vals of type ``Project`` are added to the ``Build`` so that multi-project builds can be defined entirely in .sbt files now.
For example,

::

    lazy val a = Project("a", file("a")).dependsOn(b)

    lazy val b = Project("b", file("sub")).settings(
       version := "1.0"
    )

Currently, it only makes sense to defines these in the root project's .sbt files.

A shorthand for defining Projects is provided by a new macro called `project`.
This requires the constructed Project to be directly assigned to a `val`.
The name of this val is used for the project ID and base directory.
The base directory can be changed with the `in` method.
The previous example can also be written as:

::

    lazy val a = project.dependsOn(b)

    lazy val b = project in file("sub") settings(
      version := "1.0"
    )

This macro is also available for use in Scala files.

Control over automatically added settings
-----------------------------------------

sbt loads settings from a few places in addition to the settings explicitly defined by the ``Project.settings`` field.
These include plugins, global settings, and .sbt files.
The new ``Project.autoSettings`` method configures these sources: whether to include them for the project and in what order.

``Project.autoSettings`` accepts a sequence of values of type ``AddSettings``.
Instances of ``AddSettings`` are constructed from methods in the ``AddSettings`` companion object.
The configurable settings are per-user settings (from ~/.sbt, for example), settings from .sbt files, and plugin settings (project-level only).
The order in which these instances are provided to ``autoSettings`` determines the order in which they are appended to the settings explicitly provided in ``Project.settings``.

For .sbt files, ``AddSettings.defaultSbtFiles`` adds the settings from all .sbt files in the project's base directory as usual.
The alternative method ``AddSettings.sbtFiles`` accepts a sequence of ``Files`` that will be loaded according to the standard .sbt format.
Relative files are resolved against the project's base directory.

Plugin settings may be included on a per-Plugin basis by using the ``AddSettings.plugins`` method and passing a ``Plugin => Boolean``.
The settings controlled here are only the automatic per-project settings.
Per-build and global settings will always be included.
Settings that plugins require to be manually added still need to be added manually.

For example,

::

    import AddSettings._

    lazy val root = Project("root", file(".")) autoSettings(
       userSettings, allPlugins, sbtFiles(file("explicit/a.txt"))
    )

    lazy val sub = Project("sub", file("Sub")) autoSettings(
       defaultSbtFiles, plugins(includePlugin)
    )

    def includePlugin(p: Plugin): Boolean =
       p.getClass.getName.startsWith("org.example.")

Resolving Scala dependencies
----------------------------

Scala dependencies (like scala-library and scala-compiler) are now resolved via the normal ``update`` task.  This means:

    1. Scala jars won't be copied to the boot directory, except for those needed to run sbt.
    2. Scala SNAPSHOTs behave like normal SNAPSHOTs.  In particular, running ``update`` will properly re-resolve the dynamic revision.
    3. Scala jars are resolved using the same repositories and configuration as other dependencies.
    4. Scala dependencies are not resolved via ``update`` when ``scalaHome`` is set, but are instead obtained from the configured directory.
    5. The Scala version for sbt will still be resolved via the repositories configured for the launcher.

sbt still needs access to the compiler and its dependencies in order to run ``compile``, ``console``, and other Scala-based tasks.  So, the Scala compiler jar and dependencies (like scala-reflect.jar and scala-library.jar) are defined and resolved in the ``scala-tool`` configuration (unless ``scalaHome`` is defined).  By default, this configuration and the dependencies in it are automatically added by sbt.  This occurs even when dependencies are configured in a ``pom.xml`` or ``ivy.xml`` and so it means that the version of Scala defined for your project must be resolvable by the resolvers configured for your project.

If you need to manually configure where sbt gets the Scala compiler and library used for compilation, the REPL, and other Scala tasks, do one of the following:

    1. Set ``scalaHome`` to use the existing Scala jars in a specific directory.  If ``autoScalaLibrary`` is true, the library jar found here will be added to the (unmanaged) classpath.
    2. Set ``managedScalaInstance := false`` and explicitly define ``scalaInstance``, which is of type ``ScalaInstance``.  This defines the compiler, library, and other jars comprising Scala.  If ``autoScalaLibrary`` is true, the library jar from the defined ``ScalaInstance`` will be added to the (unmanaged) classpath.

The :doc:`/Detailed-Topics/Configuring-Scala` page provides full details.
