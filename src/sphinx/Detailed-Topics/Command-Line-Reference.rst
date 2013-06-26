======================
Command Line Reference
======================

This page is a relatively complete list of command line options,
commands, and tasks you can use from the sbt interactive prompt or in
batch mode. See :doc:`Running </Getting-Started/Running>` in the Getting
Started Guide for an intro to the basics, while this page has a lot more
detail.

Notes on the command line
-------------------------

-  There is a technical distinction in sbt between *tasks*, which are
   "inside" the build definition, and *commands*, which manipulate the
   build definition itself. If you're interested in creating a command,
   see :doc:`/Extending/Commands`. This specific sbt meaning of "command" means
   there's no good general term for "thing you can type at the sbt
   prompt", which may be a setting, task, or command.
-  Some tasks produce useful values. The ``toString`` representation of
   these values can be shown using ``show <task>`` to run the task
   instead of just ``<task>``.
-  In a multi-project build, execution dependencies and the
   ``aggregate`` setting control which tasks from which projects are
   executed. See :doc:`multi-project builds </Getting-Started/Multi-Project>`.

Project-level tasks
-------------------

-  ``clean`` Deletes all generated files (the ``target`` directory).
-  ``publishLocal`` Publishes artifacts (such as jars) to the local Ivy
   repository as described in :doc:`Publishing`.
-  ``publish`` Publishes artifacts (such as jars) to the repository
   defined by the ``publishTo`` setting, described in :doc:`Publishing`.
-  ``update`` Resolves and retrieves external dependencies as described
   in :doc:`library dependencies </Getting-Started/Library-Dependencies>`.

Configuration-level tasks
-------------------------

Configuration-level tasks are tasks associated with a configuration. For
example, ``compile``, which is equivalent to ``compile:compile``,
compiles the main source code (the ``compile`` configuration).
``test:compile`` compiles the test source code (test ``test``
configuration). Most tasks for the ``compile`` configuration have an
equivalent in the ``test`` configuration that can be run using a
``test:`` prefix.

-  ``compile`` Compiles the main sources (in the ``src/main/scala``
   directory). ``test:compile`` compiles test sources (in the
   ``src/test/scala/`` directory).
-  ``console`` Starts the Scala interpreter with a classpath including
   the compiled sources, all jars in the ``lib`` directory, and managed
   libraries. To return to sbt, type ``:quit``, Ctrl+D (Unix), or Ctrl+Z
   (Windows). Similarly, ``test:console`` starts the interpreter with
   the test classes and classpath.
-  ``consoleQuick`` Starts the Scala interpreter with the project's
   compile-time dependencies on the classpath. ``test:consoleQuick``
   uses the test dependencies. This task differs from ``console`` in
   that it does not force compilation of the current project's sources.
-  ``consoleProject`` Enters an interactive session with sbt and the
   build definition on the classpath. The build definition and related
   values are bound to variables and common packages and values are
   imported. See the :doc:`consoleProject documentation <Console-Project>` for more information.
-  ``doc`` Generates API documentation for Scala source files in
   ``src/main/scala`` using scaladoc. ``test:doc`` generates API
   documentation for source files in ``src/test/scala``.
-  ``package`` Creates a jar file containing the files in
   ``src/main/resources`` and the classes compiled from
   ``src/main/scala``. ``test:package`` creates a jar containing the
   files in ``src/test/resources`` and the class compiled from
   ``src/test/scala``.
-  ``packageDoc`` Creates a jar file containing API documentation
   generated from Scala source files in ``src/main/scala``.
   ``test:packageDoc`` creates a jar containing API documentation for
   test sources files in ``src/test/scala``.
-  ``packageSrc``: Creates a jar file containing all main source files
   and resources. The packaged paths are relative to ``src/main/scala``
   and ``src/main/resources``. Similarly, ``test:packageSrc`` operates
   on test source files and resources.
-  ``run <argument>*`` Runs the main class for the project in the same
   virtual machine as ``sbt``. The main class is passed the
   ``argument``\ s provided. Please see :doc:`Running-Project-Code` for
   details on the use of ``System.exit`` and multithreading (including
   GUIs) in code run by this action. ``test:run`` runs a main class in
   the test code.
-  ``runMain <main-class> <argument>*`` Runs the specified main class
   for the project in the same virtual machine as ``sbt``. The main
   class is passed the ``argument``\ s provided. Please see :doc:`Running-Project-Code`
   for details on the use of ``System.exit`` and
   multithreading (including GUIs) in code run by this action.
   ``test:runMain`` runs the specified main class in the test code.
-  ``test`` Runs all tests detected during test compilation. See
   :doc:`Testing` for details.
-  ``testOnly <test>*`` Runs the tests provided as arguments. ``*``
   (will be) interpreted as a wildcard in the test name. See :doc:`Testing`
   for details.
-  ``testQuick <test>*`` Runs the tests specified as arguments (or all
   tests if no arguments are given) that:

   1. have not been run yet OR
   2. failed the last time they were run OR
   3. had any transitive dependencies recompiled since the last
      successful run ``*`` (will be) interpreted as a wildcard in the
      test name. See :doc:`Testing` for details.

General commands
----------------

-  ``exit`` or ``quit`` End the current interactive session or build.
   Additionally, ``Ctrl+D`` (Unix) or ``Ctrl+Z`` (Windows) will exit the
   interactive prompt.
-  ``help <command>`` Displays detailed help for the specified command.
   If the command does not exist, ``help`` lists detailed help for
   commands whose name or description match the argument, which is
   interpreted as a regular expression. If no command is provided,
   displays brief descriptions of the main commands. Related commands
   are ``tasks`` and ``settings``.
-  ``projects [add|remove <URI>]`` List all available projects if no
   arguments provided or adds/removes the build at the provided URI.
   (See :doc:`/Getting-Started/Full-Def/` for details on multi-project builds.)
-  ``project <project-id>`` Change the current project to the project
   with ID ``<project-id>``. Further operations will be done in the
   context of the given project. (See :doc:`/Getting-Started/Full-Def/` for details
   on multiple project builds.)
-  ``~ <command>`` Executes the project specified action or method
   whenever source files change. See :doc:`/Detailed-Topics/Triggered-Execution` for
   details.
-  ``< filename`` Executes the commands in the given file. Each command
   should be on its own line. Empty lines and lines beginning with '#'
   are ignored
-  ``+ <command>`` Executes the project specified action or method for
   all versions of Scala defined in the ``crossScalaVersions``
   setting.
-  ``++ <version|home-directory> <command>`` Temporarily changes the version of Scala
   building the project and executes the provided command. ``<command>``
   is optional. The specified version of Scala is used until the project
   is reloaded, settings are modified (such as by the ``set`` or
   ``session`` commands), or ``++`` is run again. ``<version>`` does not
   need to be listed in the build definition, but it must be available
   in a repository.  Alternatively, specify the path to a Scala installation.
-  ``; A ; B`` Execute A and if it succeeds, run B. Note that the
   leading semicolon is required.
-  ``eval <Scala-expression>`` Evaluates the given Scala expression and
   returns the result and inferred type. This can be used to set system
   properties, as a calculator, to fork processes, etc ... For example:

   ::

        > eval System.setProperty("demo", "true")
        > eval 1+1
        > eval "ls -l" !

Commands for managing the build definition
------------------------------------------

-  ``reload [plugins|return]`` If no argument is specified, reloads the
   build, recompiling any build or plugin definitions as necessary.
   ``reload plugins`` changes the current project to the build
   definition project (in ``project/``). This can be useful to directly
   manipulate the build definition. For example, running ``clean`` on
   the build definition project will force snapshots to be updated and
   the build definition to be recompiled. ``reload return`` changes back
   to the main project.
-  ``set <setting-expression>`` Evaluates and applies the given setting
   definition. The setting applies until sbt is restarted, the build is
   reloaded, or the setting is overridden by another ``set`` command or
   removed by the ``session`` command. See 
   :doc:`.sbt build definition </Getting-Started/Basic-Def>` and
   :doc:`Inspecting-Settings` for details.
-  ``session <command>`` Manages session settings defined by the ``set``
   command. It can persist settings configured at the prompt. See
   :doc:`Inspecting-Settings` for details.
-  ``inspect <setting-key>`` Displays information about settings, such
   as the value, description, defining scope, dependencies, delegation
   chain, and related settings. See :doc:`Inspecting-Settings` for details.

Command Line Options
--------------------

System properties can be provided either as JVM options, or as SBT
arguments, in both cases as ``-Dprop=value``. The following properties
influence SBT execution. Also see :doc:`Launcher`.

+------------------------------+-----------+---------------------+----------------------------------------------------+
| Property                     | Values    | Default             | Meaning                                            |
+==============================+===========+=====================+====================================================+
| ``sbt.log.noformat``         | Boolean   | false               | If true, disable ANSI color codes. Useful on build |
|                              |           |                     | servers or terminals that don't support color.     |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.global.base``          | Directory | ~/.sbt              | The directory containing global settings and       |
|                              |           |                     | plugins                                            |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.ivy.home``             | Directory | ~/.ivy2             | The directory containing the local Ivy repository  |
|                              |           |                     | and artifact cache                                 |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.boot.directory``       | Directory | ~/.sbt/boot         | Path to shared boot directory                      |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.main.class``           | String    |                     |                                                    |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``xsbt.inc.debug``           | Boolean   | false               |                                                    |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.extraClasspath``       | Classpath |                     | A list of classpath entries (jar files or          |
|                              | Entries   |                     | directories) that are added to sbt's classpath.    |
|                              |           |                     | Note that the entries are deliminted by comma,     |
|                              |           |                     | e.g.: ``entry1, entry2,..``. See also              |
|                              |           |                     | ``resources`` in the :doc:`Launcher`               |
|                              |           |                     | documentation.                                     |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.version``              | Version   | 0.11.3              | sbt version to use, usually taken from             |
|                              |           |                     | project/build.properties                           |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.boot.properties``      | File      |                     |                                                    |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.override.build.repos`` | Boolean   | false               | If true, repositories configured in a build        |
|                              |           |                     | definition are ignored and the repositories        |
|                              |           |                     | configured for the launcher are used instead. See  |
|                              |           |                     | ``sbt.repository.config`` and the :doc:`Launcher`  |
|                              |           |                     | documentation.                                     |
+------------------------------+-----------+---------------------+----------------------------------------------------+
| ``sbt.repository.config``    | File      | ~/.sbt/repositories | A file containing the repositories to use for the  |
|                              |           |                     | launcher. The format is the same as a              |
|                              |           |                     | ``[repositories]`` section for a :doc:`Launcher`   |
|                              |           |                     | configuration file. This setting is typically used |
|                              |           |                     | in conjuction with setting                         |
|                              |           |                     | ``sbt.override.build.repos`` to true (see previous |
|                              |           |                     | row and the :doc:`Launcher` documentation).        |
+------------------------------+-----------+---------------------+----------------------------------------------------+
