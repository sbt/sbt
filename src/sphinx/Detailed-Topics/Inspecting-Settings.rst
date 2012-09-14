=========================================
Interacting with the Configuration System
=========================================

Central to sbt is the new configuration system, which is designed to
enable extensive customization. The goal of this page is to explain the
general model behind the configuration system and how to work with it.
The Getting Started Guide (see :doc:`.sbt files </Getting-Started/Basic-Def>`)
describes how to define settings; this page describes interacting
with them and exploring them at the command line.

Selecting commands, tasks, and settings
=======================================

A fully-qualified reference to a setting or task looks like:

::

    {<build-uri>}<project-id>/config:inkey::key

This "scoped key" reference is used by commands like ``last`` and
``inspect`` and when selecting a task to run. Only ``key`` is usually
required by the parser; the remaining optional pieces select the scope.
These optional pieces are individually referred to as scope axes. In the
above description, ``{<build-uri>}`` and ``<project-id>/`` specify the
project axis, ``config:`` is the configuration axis, and ``inkey`` is
the task-specific axis. Unspecified components are taken to be the
current project (project axis) or auto-detected (configuration and task
axes). An asterisk (``*``) is used to explicitly refer to the ``Global``
context, as in ``*/*:key``.

Selecting the configuration
---------------------------

In the case of an unspecified configuration (that is, when the
``config:`` part is omitted), if the key is defined in ``Global``, that
is selected. Otherwise, the first configuration defining the key is
selected, where order is determined by the project definition's
``configurations`` member. By default, this ordering is
``compile, test, ...``

For example, the following are equivalent when run in a project ``root``
in the build in ``/home/user/sample/``:

::

    > compile
    > compile:compile
    > root/compile
    > root/compile:compile
    > {file:/home/user/sample/}root/compile:compile

As another example, ``run`` by itself refers to ``compile:run`` because
there is no global ``run`` task and the first configuration searched,
``compile``, defines a ``run``. Therefore, to reference the ``run`` task
for the ``test`` configuration, the configuration axis must be specified
like ``test:run``. Some other examples that require the explicit
``test:`` axis:

::

    > test:console-quick
    > test:console
    > test:doc
    > test:package

Task-specific Settings
----------------------

Some settings are defined per-task. This is used when there are several
related tasks, such as ``package``, ``package-src``, and
``package-doc``, in the same configuration (such as ``compile`` or
``test``). For package tasks, their settings are the files to package,
the options to use, and the output file to produce. Each package task
should be able to have different values for these settings.

This is done with the task axis, which selects the task to apply a
setting to. For example, the following prints the output jar for the
different package tasks.

::

    > package::artifact-path
    [info] /home/user/sample/target/scala-2.8.1.final/demo_2.8.1-0.1.jar

    > package-src::artifact-path
    [info] /home/user/sample/target/scala-2.8.1.final/demo_2.8.1-0.1-src.jar

    > package-doc::artifact-path
    [info] /home/user/sample/target/scala-2.8.1.final/demo_2.8.1-0.1-doc.jar

    > test:package::artifact-path
    [info] /home/user/sample/target/scala-2.8.1.final/root_2.8.1-0.1-test.jar

Note that a single colon ``:`` follows a configuration axis and a double
colon ``::`` follows a task axis.

Discovering Settings and Tasks
==============================

This section discusses the ``inspect`` command, which is useful for
exploring relationships between settings. It can be used to determine
which setting should be modified in order to affect another setting, for
example.

Value and Provided By
---------------------

The first piece of information provided by ``inspect`` is the type of a
task or the value and type of a setting. The following section of output
is labeled "Provided by". This shows the actual scope where the setting
is defined. For example,

::

    > inspect library-dependencies
    [info] Setting: scala.collection.Seq[sbt.ModuleID] = List(org.scalaz:scalaz-core:6.0-SNAPSHOT, org.scala-tools.testing:scalacheck:1.8:test)
    [info] Provided by:
    [info]  {file:/home/user/sample/}root/*:library-dependencies
    ...

This shows that ``library-dependencies`` has been defined on the current
project (``{file:/home/user/sample/}root``) in the global configuration
(``*:``). For a task like ``update``, the output looks like:

::

    > inspect update
    [info] Task: sbt.UpdateReport
    [info] Provided by:
    [info]  {file:/home/user/sample/}root/*:update
    ...

Related Settings
----------------

The "Related" section of ``inspect`` output lists all of the definitions
of a key. For example,

::

    > inspect compile
    ...
    [info] Related:
    [info]  test:compile

This shows that in addition to the requested ``compile:compile`` task,
there is also a ``test:compile`` task.

Dependencies
------------

Forward dependencies show the other settings (or tasks) used to define a
setting (or task). Reverse dependencies go the other direction, showing
what uses a given setting. ``inspect`` provides this information based
on either the requested dependencies or the actual dependencies.
Requested dependencies are those that a setting directly specifies.
Actual settings are what those dependencies get resolved to. This
distinction is explained in more detail in the following sections.

Requested Dependencies
~~~~~~~~~~~~~~~~~~~~~~

As an example, we'll look at ``console``:

::

    > inspect console
    ...
    [info] Dependencies:
    [info]  compile:console::full-classpath
    [info]  compile:console::scalac-options
    [info]  compile:console::initial-commands
    [info]  compile:console::cleanup-commands
    [info]  compile:console::compilers
    [info]  compile:console::task-temporary-directory
    [info]  compile:console::scala-instance
    [info]  compile:console::streams

    ...

This shows the inputs to the ``console`` task. We can see that it gets
its classpath and options from ``full-classpath`` and
``scalac-options(for console)``. The information provided by the
``inspect`` command can thus assist in finding the right setting to
change. The convention for keys, like ``console`` and
``full-classpath``, is that the Scala identifier is camel case, while
the String representation is lowercase and separated by dashes. The
Scala identifier for a configuration is uppercase to distinguish it from
tasks like ``compile`` and ``test``. For example, we can infer from the
previous example how to add code to be run when the Scala interpreter
starts up:

::

    > set initialCommands in Compile in console := "import mypackage._"
    > console
    ...
    import mypackage._
    ...

``inspect`` showed that ``console`` used the setting
``compile:console::initial-commands``. Translating the
``initial-commands`` string to the Scala identifier gives us
``initialCommands``. ``compile`` indicates that this is for the main
sources. ``console::`` indicates that the setting is specific to
``console``. Because of this, we can set the initial commands on the
``console`` task without affecting the ``console-quick`` task, for
example.

Actual Dependencies
~~~~~~~~~~~~~~~~~~~

``inspect actual <scoped-key>`` shows the actual dependency used. This
is useful because delegation means that the dependency can come from a
scope other than the requested one. Using ``inspect actual``, we see
exactly which scope is providing a value for a setting. Combining
``inspect actual`` with plain ``inspect``, we can see the range of
scopes that will affect a setting. Returning to the example in Requested
Dependencies,

::

    > inspect actual console
    ...
    [info] Dependencies:
    [info]  compile:scalac-options
    [info]  compile:full-classpath
    [info]  *:scala-instance
    [info]  */*:initial-commands
    [info]  */*:cleanup-commands
    [info]  */*:task-temporary-directory
    [info]  *:console::compilers
    [info]  compile:console::streams
    ...

For ``initial-commands``, we see that it comes from the global scope
(``*/*:``). Combining this with the relevant output from
``inspect console``:

::

    compile:console::initial-commands

we know that we can set ``initial-commands`` as generally as the global
scope, as specific as the current project's ``console`` task scope, or
anything in between. This means that we can, for example, set
``initial-commands`` for the whole project and will affect ``console``:

::

    > set initialCommands := "import mypackage._"
    ...

The reason we might want to set it here this is that other console tasks
will use this value now. We can see which ones use our new setting by
looking at the reverse dependencies output of ``inspect actual``:

::

    > inspect actual initial-commands
    ...
    [info] Reverse dependencies:
    [info]  test:console
    [info]  compile:console-quick
    [info]  compile:console
    [info]  test:console-quick
    [info]  *:console-project
    ...

We now know that by setting ``initial-commands`` on the whole project,
we affect all console tasks in all configurations in that project. If we
didn't want the initial commands to apply for ``console-project``, which
doesn't have our project's classpath available, we could use the more
specific task axis:

``console > set initialCommands in console := "import mypackage._" > set initialCommands in consoleQuick := "import mypackage._"``
or configuration axis:

::

    > set initialCommands in Compile := "import mypackage._"
    > set initialCommands in Test := "import mypackage._"

The next part describes the Delegates section, which shows the chain of
delegation for scopes.

Delegates
---------

A setting has a key and a scope. A request for a key in a scope A may be
delegated to another scope if A doesn't define a value for the key. The
delegation chain is well-defined and is displayed in the Delegates
section of the ``inspect`` command. The Delegates section shows the
order in which scopes are searched when a value is not defined for the
requested key.

As an example, consider the initial commands for ``console`` again:

::

    > inspect console::initial-commands
    ...
    [info] Delegates:
    [info]  *:console::initial-commands
    [info]  *:initial-commands
    [info]  {.}/*:console::initial-commands
    [info]  {.}/*:initial-commands
    [info]  */*:console::initial-commands
    [info]  */*:initial-commands
    ...

This means that if there is no value specifically for
``*:console::initial-commands``, the scopes listed under Delegates will
be searched in order until a defined value is found.
