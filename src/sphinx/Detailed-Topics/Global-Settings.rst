===============
Global Settings
===============

Basic global configuration file
-------------------------------

Settings that should be applied to all projects can go in
``~/.sbt/global.sbt`` (or any file in ``~/.sbt/`` with a ``.sbt``
extension). Plugins that are defined globally in ``~/.sbt/plugins`` are
available to these settings. For example, to change the default
``shellPrompt`` for your projects:

``~/.sbt/global.sbt``

::

    shellPrompt := { state =>
     "sbt (%s)> ".format(Project.extract(state).currentProject.id)
    }

Global Settings using a Global Plugin
-------------------------------------

The ``~/.sbt/plugins`` directory is a global plugin project. This can be
used to provide global commands, plugins, or other code.

To add a plugin globally, create ``~/.sbt/plugins/build.sbt`` containing
the dependency definitions. For example:

::

    addSbtPlugin("org.example" % "plugin" % "1.0")

To change the default ``shellPrompt`` for every project using this
approach, create a local plugin ``~/.sbt/plugins/ShellPrompt.scala``:

::

    import sbt._
    import Keys._

    object ShellPrompt extends Plugin {
      override def settings = Seq(
        shellPrompt := { state =>
          "sbt (%s)> ".format(Project.extract(state).currentProject.id) }
      )
    }

The ``~/.sbt/plugins`` directory is a full project that is included as
an external dependency of every plugin project. In practice, settings
and code defined here effectively work as if they were defined in a
project's ``project/`` directory. This means that ``~/.sbt/plugins`` can
be used to try out ideas for plugins such as shown in the shellPrompt
example.
