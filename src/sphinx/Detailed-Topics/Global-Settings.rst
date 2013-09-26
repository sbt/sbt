===============
Global Settings
===============

Basic global configuration file
-------------------------------

Settings that should be applied to all projects can go in :sublit:`|globalSbtFile|`
(or any file in :sublit:`|globalBase|` with a `.sbt` extension).
Plugins that are defined globally in :sublit:`|globalPluginsBase|` are
available to these settings. For example, to change the default
:key:`shellPrompt` for your projects:

:sublit:`|globalSbtFile|`

::

    shellPrompt := { state =>
     "sbt (%s)> ".format(Project.extract(state).currentProject.id)
    }

Global Settings using a Global Plugin
-------------------------------------

The :sublit:`|globalPluginsBase|` directory is a global plugin project. This can be
used to provide global commands, plugins, or other code.

To add a plugin globally, create :sublit:`|globalPluginSbtFile|` containing
the dependency definitions. For example:

::

    addSbtPlugin("org.example" % "plugin" % "1.0")

To change the default :key:`shellPrompt` for every project using this
approach, create a local plugin :sublit:`|globalShellPromptScala|`:

::

    import sbt._
    import Keys._

    object ShellPrompt extends Plugin {
      override def settings = Seq(
        shellPrompt := { state =>
          "sbt (%s)> ".format(Project.extract(state).currentProject.id) }
      )
    }

The :sublit:`|globalPluginsBase|` directory is a full project that is included as
an external dependency of every plugin project. In practice, settings
and code defined here effectively work as if they were defined in a
project's `project/` directory. This means that :sublit:`|globalPluginsBase|` can
be used to try out ideas for plugins such as shown in the :key:`shellPrompt`
example.

.. |globalBase| replace:: ~/.sbt/|version|/
.. |globalPluginsBase| replace:: |globalBase|\ plugins/
.. |globalSbtFile| replace:: |globalBase|\ global.sbt
.. |globalPluginSbtFile| replace:: |globalPluginsBase|\ build.sbt
.. |globalShellPromptScala| replace:: |globalPluginsBase|\ ShellPrompt.scala`
