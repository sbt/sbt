===============
Global Settings
===============

Basic global configuration file
-------------------------------

Settings that should be applied to all projects can go in `|globalSbtFile|`
(or any file in `|globalBase|` with a `.sbt` extension).
Plugins that are defined globally in `|globalPluginsBase|` are
available to these settings. For example, to change the default
`shellPrompt` for your projects:

`|globalSbtFile|`

::

    shellPrompt := { state =>
     "sbt (%s)> ".format(Project.extract(state).currentProject.id)
    }

Global Settings using a Global Plugin
-------------------------------------

The `|globalPluginsBase|` directory is a global plugin project. This can be
used to provide global commands, plugins, or other code.

To add a plugin globally, create `|globalPluginSbtFile|` containing
the dependency definitions. For example:

::

    addSbtPlugin("org.example" % "plugin" % "1.0")

To change the default `shellPrompt` for every project using this
approach, create a local plugin `|globalShellPromptScala|`:

::

    import sbt._
    import Keys._

    object ShellPrompt extends Plugin {
      override def settings = Seq(
        shellPrompt := { state =>
          "sbt (%s)> ".format(Project.extract(state).currentProject.id) }
      )
    }

The `|globalPluginsBase|` directory is a full project that is included as
an external dependency of every plugin project. In practice, settings
and code defined here effectively work as if they were defined in a
project's `project/` directory. This means that `|globalPluginsBase|` can
be used to try out ideas for plugins such as shown in the `shellPrompt`
example.

.. |globalBase| replace:: ~/.sbt/|version|/
.. |globalPluginsBase| replace:: |globalBase|\ plugins/
.. |globalSbtFile| replace:: |globalBase|\ global.sbt
.. |globalPluginSbtFile| replace:: |globalPluginsBase|\ build.sbt
.. |globalShellPromptScala| replace:: |globalPluginsBase|\ ShellPrompt.scala`
