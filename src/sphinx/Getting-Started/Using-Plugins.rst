=============
Using Plugins
=============

Please read the earlier pages in the Getting Started Guide first, in
particular you need to understand :doc:`build.sbt <Basic-Def>` and
:doc:`library dependencies <Library-Dependencies>`,
before reading this page.

What is a plugin?
-----------------

A plugin extends the build definition, most commonly by adding new
settings. The new settings could be new tasks. For example, a plugin
could add a `codeCoverage` task which would generate a test coverage
report.

Declaring a plugin
------------------

If your project is in directory `hello`, edit `hello/project/plugins.sbt` and declare the plugin dependency by passing the plugin's Ivy module ID to `addSbtPlugin`: ::

    addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.7.0")

Not every plugin is located on one of the default repositories and a plugin's documentation may instruct you to also add the repository where it can be found: ::

    resolvers += ...

Plugins usually provide settings that get added to a project to enable the plugin's functionality.
This is described in the next section.


Adding settings for a plugin
----------------------------

A plugin can declare that its settings be automatically added, in which case you don't have to do anything to add them.

As of sbt 0.13.2, there is a new :doc:`auto-plugins <../DetailedTopics/AutoPlugins>` feature that enables plugins
to automatically, and safely, ensure their settings and dependencies are on a project.  Most plugins should have
their default settings automatically, however some may require explicit enablement.

If you're using a plugin that requires explicit enablement, then you you have to add the following to your
`build.sbt` ::

    lazy val util = project.setPlugins(ThePluginIWant)

Most plugins document whether they need to explicitly enabled.  If you're curious which plugins are enabled
for a given project, just run the `plugins` command on the sbt console.

For example ::

    > plugins
    In file:/home/jsuereth/projects/sbt/test-ivy-issues/
      sbt.plugins.IvyModule: enabled in test-ivy-issues
      sbt.plugins.JvmModule: enabled in test-ivy-issues
      sbt.plugins.GlobalModule: enabled in test-ivy-issues


Here, the plugins output is showing that the sbt default plugins are all enabled.  Sbt's default settings are provided via three plugins:

1. GlobalModule:  Provides the core parallelism controls for tasks
2. IvyModule:     Provides the mechanisms to publish/resolve modules.
3. JvmModule:     Provides the mechanisms to compile/test/run/package Java/Scala projects.


However, older plugins often required settings to be added explictly, so that :doc:`multi-project build <Multi-Project>` could have different types of projects.  The plugin documentation will indicate how to configure it, but typically for older plugins this involves adding the base settings for the plugin and customizing as necessary.

For example, for the sbt-site plugin, add ::

    site.settings

to a `build.sbt` to enable it for that project.

If the build defines multiple projects, instead add it directly to the project: ::

    // don't use the site plugin for the `util` project
    lazy val util = project

    // enable the site plugin for the `core` project
    lazy val core = project.settings( site.settings : _*)


Global plugins
--------------

Plugins can be installed for all your projects at once by dropping them in :sublit:`|globalPluginsBase|`.
:sublit:`|globalPluginsBase|` is an sbt project whose classpath is exported to all sbt build definition projects.
Roughly speaking, any `.sbt` or `.scala` files in :sublit:`|globalPluginsBase|` behave as if they were in the `project/` directory for all projects.

You can create :sublit:`|globalPluginsBase|\ build.sbt` and put `addSbtPlugin()`
expressions in there to add plugins to all your projects at once.
This feature should be used sparingly, however.
See :ref:`Best Practices <global-vs-local-plugins>`.

Available Plugins
-----------------

There's :doc:`a list of available plugins </Community/Community-Plugins>`.

Some especially popular plugins are:

-  those for IDEs (to import an sbt project into your IDE)
-  those supporting web frameworks, such as
   `xsbt-web-plugin <https://github.com/JamesEarlDouglas/xsbt-web-plugin>`_.

:doc:`Check out the list</Community/Community-Plugins>`.


Creating a Plugin
-----------------

A minimal plugin is a Scala library that is built against the version of Scala for sbt itself, which is currently |scalaVersion|.
Nothing special needs to be done for this type of plugin.
It can be published as a normal project and declared in `project/plugins.sbt` like a normal dependency (without `addSbtPlugin`).

A more typical plugin will provide sbt tasks, commands, or settings. 
This kind of plugin may provide these settings automatically or make them available for the user to explicitly integrate.
To create an sbt plugin,

  1. Create a new project for the plugin.
  2. Set `sbtPlugin := true` for the project in `build.sbt`.  This adds a dependency on sbt and will detect and record Plugins that you define.
  3. Define another `object` that extends `AutoImport`. The contents of this object will be automatically imported in `.sbt` files, so ensure it only contains important API definitions and types.
  4. Define an `object` that extends `AutoPlugin`.
  5. Declare dependencies on other plugins by defining the `requires` method.
  5. Define any custom tasks or settings (see the next section :doc:`Custom-Settings`).
  6. Collect the default settings to apply to a project in a list for the user to add.  Optionally override one or more of `AutoPlugin`'s methods to have settings automatically added to user projects.
  8. Publish the project.  There is a  :doc:`community repository </Community/Community-Plugins>` available for open source plugins.

For more details, including ways of developing plugins, see :doc:`/Extending/Plugins`.
For best practices, see :doc:`/Extending/Plugins-Best-Practices`.

Next
----

Move on to create :doc:`custom settings <Custom-Settings>`.

.. |globalBase| replace:: ~/.sbt/|version|/
.. |globalPluginsBase| replace:: |globalBase|\ plugins/
