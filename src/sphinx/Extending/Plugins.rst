=======
Plugins
=======

Introduction
============

A plugin is essentially a way to use external code in a build
definition. A plugin can be a library used to implement a task. For
example, you might use
`Knockoff <https://github.com/tristanjuricek/knockoff/>`_ to write a
markdown processing task. A plugin can define a sequence of sbt Settings
that are automatically added to all projects or that are explicitly
declared for selected projects. For example, a plugin might add a
`proguard` task and associated (overridable) settings.
Also, :doc:`Commands` can be added with the :key:`commands` setting

The :doc:`Plugins-Best-Practices` page describes the
currently evolving guidelines to writing sbt plugins. See also the
general :doc:`/Detailed-Topics/Best-Practices`.

Using a binary sbt plugin
=========================

A common situation is using a binary plugin published to a repository.
Create `project/plugins.sbt` with the desired sbt plugins, any general
dependencies, and any necessary repositories:

::

    addSbtPlugin("org.example" % "plugin" % "1.0")

    addSbtPlugin("org.example" % "another-plugin" % "2.0")

    // plain library (not an sbt plugin) for use in the build definition
    libraryDependencies += "org.example" % "utilities" % "1.3"

    resolvers += "Example Plugin Repository" at "http://example.org/repo/"

See the rest of the page for more information on creating and using
plugins.

By Description
==============

A plugin definition is a project in `<main-project>/project/`. This
project's classpath is the classpath used for build definitions in
`<main-project>/project/` and any `.sbt` files in the project's base
directory. It is also used for the `eval` and `set` commands.

Specifically,

1. Managed dependencies declared by the `project/` project are
   retrieved and are available on the build definition classpath, just
   like for a normal project.
2. Unmanaged dependencies in `project/lib/` are available to the build
   definition, just like for a normal project.
3. Sources in the `project/` project are the build definition files
   and are compiled using the classpath built from the managed and
   unmanaged dependencies.
4. Project dependencies can be declared in `project/plugins.sbt` or
   `project/project/Build.scala` and will be available to the build
   definition sources. Think of `project/project/` as the build
   definition for the build definition.

The build definition classpath is searched for `sbt/sbt.plugins`
descriptor files containing the names of Plugin implementations. A
Plugin is a module that defines settings to automatically inject to
projects. Additionally, all Plugin modules are wildcard imported for the
`eval` and `set` commands and `.sbt` files. A Plugin
implementation is not required to produce a plugin, however. It is a
convenience for plugin consumers and because of the automatic nature, it
is not always appropriate.

The `reload plugins` command changes the current build to
`<current-build>/project/`. This allows manipulating the build
definition project like a normal project. `reload return` changes back
to the original build. Any session settings for the plugin definition
project that have not been saved are dropped.

*Note*: At runtime, all plugins for all builds are loaded in a separate, parent class loader of the class loaders for builds.
This means that plugins will not see classes or resources from build definitions.

Global plugins
--------------

The :sublit:`|globalPluginsBase|` directory is treated as a global plugin
definition project. It is a normal sbt project whose classpath is
available to all sbt project definitions for that user as described
above for per-project plugins.

By Example
==========

Using a library in a build definition
-------------------------------------

As an example, we'll add the `xsbt-web-plugin
<https://github.com/JamesEarlDouglas/xsbt-web-plugin>`_.

1) Automatically managed: direct editing approach
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Edit `project/plugins.sbt` to contain:

::

    addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "0.5.0")

If sbt is running, do `reload`.

2) Use the library
~~~~~~~~~~~~~~~~~~

xsbt-web-plugin is ready to be used in build definitions. This includes
the `eval` and `set` commands and `.sbt` and `project/*.scala`
files.

.. code-block:: console

    > eval com.earldouglas.xsbtwebplugin.PluginKeys.DefaultConf

This outputs:

.. code-block:: console

    [info] ans: sbt.Configuration = compile

See the `xsbt-web-plugin
<https://github.com/JamesEarlDouglas/xsbt-web-plugin>`_ documentation for further usage information.

Creating a plugin
=================

Introduction
------------

A minimal plugin is a Scala library that is built against the version of
Scala that sbt runs (currently, |scalaRelease|) or a Java library. Nothing
special needs to be done for this type of library, as shown in the
previous section. A more typical plugin will provide sbt tasks,
commands, or settings. This kind of plugin may provide these settings
automatically or make them available for the user to explicitly
integrate.

Description
-----------

To make a plugin, create a project and configure `sbtPlugin` to
`true`. Then, write the plugin code and publish your project to a
repository. The plugin can be used as described in the previous section.

A plugin can implement `sbt.Plugin`. The contents of a Plugin
singleton, declared like `object MyPlugin extends Plugin`, are
wildcard imported in `set`, `eval`, and `.sbt` files. Typically,
this is used to provide new keys (SettingKey, TaskKey, or InputKey) or
core methods without requiring an import or qualification.

In addition, a `Plugin` can implement `projectSettings`, `buildSettings`, and `globalSettings` as appropriate.
The Plugin's `projectSettings` is automatically appended to each project's settings.
The `buildSettings` is appended to each build's settings (that is, `in ThisBuild`).
The `globalSettings` is appended once to the global settings (`in Global`).
These allow a plugin to automatically provide new functionality or new defaults.
One main use of this feature is to globally add commands, such as for IDE plugins.
Use `globalSettings` to define the default value of a setting.

These automatic features should be used judiciously because the automatic activation generally reduces control for the build author (the user of the plugin).
Some control is returned to them via `Project.autoSettings`, which changes how automatically added settings are added and in what order.

Example Plugin
--------------

An example of a typical plugin:

`build.sbt`:

::

    sbtPlugin := true

    name := "example-plugin"

    organization := "org.example"

`MyPlugin.scala`:

::

    import sbt._
    object MyPlugin extends Plugin
    {
        // configuration points, like the built in `version`, `libraryDependencies`, or `compile`
        // by implementing Plugin, these are automatically imported in a user's `build.sbt`
        val newTask = taskKey[Unit]("A new task.")
        val newSetting = settingKey[String]("A new setting.")

        // a group of settings ready to be added to a Project
        // to automatically add them, do 
        val newSettings = Seq(
            newSetting := "test",
            newTask := println(newSetting.value)
        )

        // alternatively, by overriding `settings`, they could be automatically added to a Project
        // override val settings = Seq(...)
    }

Usage example
-------------

A build definition that uses the plugin might look like:

`build.sbt`

::

    MyPlugin.newSettings

    newSetting := "example"

Example command plugin
----------------------

A basic plugin that adds commands looks like:

`build.sbt`

::

    sbtPlugin := true

    name := "example-plugin"

    organization := "org.example"

`MyPlugin.scala`

::

    import sbt._
    import Keys._
    object MyPlugin extends Plugin
    {
      override lazy val settings = Seq(commands += myCommand)

      lazy val myCommand = 
        Command.command("hello") { (state: State) =>
          println("Hi!")
          state
        }
    }

This example demonstrates how to take a Command (here, `myCommand`)
and distribute it in a plugin. Note that multiple commands can be
included in one plugin (for example, use `commands ++= Seq(a,b)`). See
:doc:`Commands` for defining more useful commands, including ones that
accept arguments and affect the execution state.

Global plugins example
----------------------

The simplest global plugin definition is declaring a library or plugin
in :sublit:`|globalPluginsBase|\ build.sbt`:

::

    libraryDependencies += "org.example" %% "example-plugin" % "0.1"

This plugin will be available for every sbt project for the current
user.

In addition:

1. Jars may be placed directly in :sublit:`|globalPluginsBase|\ lib/` and will be
   available to every build definition for the current user.
2. Dependencies on plugins built from source may be declared in
   :sublit:`|globalPluginsBase|\ project/Build.scala` as described at
   :doc:`/Getting-Started/Full-Def`.
3. A Plugin may be directly defined in Scala source files in
   :sublit:`|globalPluginsBase|`, such as :sublit:`|globalPluginsBase|\ MyPlugin.scala`.
   :sublit:`|globalPluginsBase|\ /build.sbt` should contain `sbtPlugin := true`.
   This can be used for quicker turnaround when developing a plugin
   initially:

   1. Edit the global plugin code
   2. `reload` the project you want to use the modified plugin in
   3. sbt will rebuild the plugin and use it for the project.
      Additionally, the plugin will be available in other projects on
      the machine without recompiling again. This approach skips the
      overhead of :key:`publishLocal` and cleaning the plugins directory
      of the project using the plugin.

These are all consequences of :sublit:`|globalPluginsBase|` being a standard
project whose classpath is added to every sbt project's build
definition.

Best Practices
==============

If you're a plugin writer, please consult the :doc:`Plugins-Best-Practices`
page; it contains a set of guidelines to help you ensure that your
plugin is consistent with and plays well with other plugins.

.. |globalBase| replace:: ~/.sbt/|version|/
.. |globalPluginsBase| replace:: |globalBase|\ plugins/
