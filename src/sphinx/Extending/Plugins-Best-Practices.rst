======================
Plugins Best Practices
======================

*This page is intended primarily for SBT plugin authors.*

A plugin developer should strive for consistency and ease of use.
Specifically:

-  Plugins should play well with other plugins. Avoiding namespace
   clashes (in both SBT and Scala) is paramount.
-  Plugins should follow consistent conventions. The experiences of an
   SBT *user* should be consistent, no matter what plugins are pulled
   in.

Here are some current plugin best practices. **NOTE:** Best practices
are evolving, so check back frequently.

Avoid overriding ``settings``
-----------------------------

SBT will automatically load your plugin's ``settings`` into the build.
Overriding ``val settings`` should only be done by plugins intending to
provide commands. Regular plugins defining tasks and settings should
provide a sequence named after the plugin like so:

::

    val obfuscateSettings = Seq(...)

This allows build user to choose which subproject the plugin would be
used. See later section for how the settings should be scoped.

Reuse existing keys
-------------------

SBT has a number of `predefined keys <../../api/sbt/Keys%24.html>`_.
Where possible, reuse them in your plugin. For instance, don't define:

::

    val sourceFiles = SettingKey[Seq[File]]("sourceFiles")

Instead, simply reuse SBT's existing ``sources`` key.

Avoid namespace clashes
-----------------------

Sometimes, you need a new key, because there is no existing SBT key. In
this case, use a plugin-specific prefix, both in the (string) key name
used in the SBT namespace and in the Scala ``val``. There are two
acceptable ways to accomplish this goal.

Just use a ``val`` prefix
~~~~~~~~~~~~~~~~~~~~~~~~~

::

    package sbtobfuscate
    object Plugin extends sbt.Plugin {
      val obfuscateStylesheet = SettingKey[File]("obfuscateStylesheet")
    }

In this approach, every ``val`` starts with ``obfuscate``. A user of the
plugin would refer to the settings like this:

::

    obfuscateStylesheet := ...

Use a nested object
~~~~~~~~~~~~~~~~~~~

::

    package sbtobfuscate
    object Plugin extends sbt.Plugin {
      object ObfuscateKeys {
        val stylesheet = SettingKey[File]("obfuscateStylesheet")
      }
    }

In this approach, all non-common settings are in a nested object. A user
of the plugin would refer to the settings like this:

::

    import ObfuscateKeys._ // place this at the top of build.sbt

    stylesheet := ...

Configuration Advice
--------------------

Due to usability concerns from the shell, you could opt out of
task-scoping described in this section, if your plugin makes heavy use
of the shell. Using configuration-scoping the user could discover your
tasks using tab completion:

.. code-block:: console

    coffee:[tab]

This method no longer works with per-task keys, but there's a pending
case, so hopefully it will be addressed in the future.

When to define your own configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If your plugin introduces a new concept (even if that concept reuses an
existing key), you want your own configuration. For instance, suppose
you've built a plugin that produces PDF files from some kind of markup,
and your plugin defines a target directory to receive the resulting
PDFs. That target directory is scoped in its own configuration, so it is
distinct from other target directories. Thus, these two definitions use
the same *key*, but they represent distinct *values*. So, in a user's
``build.sbt``, we might see:

::

    target in PDFPlugin := baseDirectory.value / "mytarget" / "pdf"
    target in Compile := baseDirectory.value / "mytarget"

In the PDF plugin, this is achieved with an ``inConfig`` definition:

::

    val settings: Seq[sbt.Project.Setting[_]] = inConfig(LWM)(Seq(
      target := baseDirectory.value / "target" / "docs" # the default value
    ))

When *not* to define your own configuration.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you're merely adding to existing definitions, don't define your own
configuration. Instead, reuse an existing one *or* scope by the main
task (see below).

::

    val akka = config("akka")  // This isn't needed.
    val akkaStartCluster = TaskKey[Unit]("akkaStartCluster")

    target in akkaStartCluster := ... // This is ok.
    akkaStartCluster in akka := ...   // BAD.  No need for a Config for plugin-specific task.

Configuration Cat says "Configuration is for configuration"
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When defining a new type of configuration, e.g.

::

    val Config = config("profile")

should be used to create a "cross-task" configuration. The task
definitions don't change in this case, but the default configuration
does. For example, the ``profile`` configuration can extend the test
configuration with additional settings and changes to allow profiling in
SBT. Plugins should not create arbitrary Configurations, but utilize
them for specific purposes and builds.

Configurations actually tie into dependency resolution (with Ivy) and
can alter generated pom files.

Configurations should *not* be used to namespace keys for a plugin. e.g.

::

    val Config = config("my-plugin")
    val pluginKey = SettingKey[String]("pluginSpecificKey")
    val settings = pluginKey in Config  // DON'T DO THIS!

Playing nice with configurations
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Whether you ship with a configuration or not, a plugin should strive to
support multiple configurations, including those created by the build
user. Some tasks that are tied to a particular configuration can be
re-used in other configurations. While you may not see the need
immediately in your plugin, some project may and will ask you for the
flexibility.

Provide raw settings and configured settings
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Split your settings by the configuration axis like so:

::

    val obfuscate = TaskKey[Seq[File]]("obfuscate")
    val obfuscateSettings = inConfig(Compile)(baseObfuscateSettings)
    val baseObfuscateSettings: Seq[Setting[_]] = Seq(
      obfuscate := ... (sources in obfuscate).value ...,
      sources in obfuscate := sources.value
    )

The ``baseObfuscateSettings`` value provides base configuration for the
plugin's tasks. This can be re-used in other configurations if projects
require it. The ``obfuscateSettings`` value provides the default
``Compile`` scoped settings for projects to use directly. This gives the
greatest flexibility in using features provided by a plugin. Here's how
the raw settings may be reused:

::

    Project.inConfig(Test)(sbtObfuscate.Plugin.baseObfuscateSettings)

Alternatively, one could provide a utility method to load settings in a
given configuration:

::

    def obfuscateSettingsIn(c: Configuration): Seq[Project.Setting[_]] =
      inConfig(c)(baseObfuscateSettings)

This could be used as follows:

::

    seq(obfuscateSettingsIn(Test): _*) 

Using a 'main' task scope for settings
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Sometimes you want to define some settings for a particular 'main' task
in your plugin. In this instance, you can scope your settings using the
task itself.

::

    val obfuscate = TaskKey[Seq[File]]("obfuscate")
    val obfuscateSettings = inConfig(Compile)(baseObfuscateSettings)
    val baseObfuscateSettings: Seq[Setting[_]] = Seq(
      obfuscate := ... (sources in obfuscate).value ...,
      sources in obfuscate := sources.value
    )

In the above example, ``sources in obfuscate`` is scoped under the main
task, ``obfuscate``.

Mucking with Global build state
-------------------------------

There may be times when you need to muck with global build state. The
general rule is *be careful what you touch*.

First, make sure your user does not include global build configuration in
*every* project but rather in the build itself. e.g.

::

    object MyBuild extends Build {
      override lazy val settings = super.settings ++ MyPlugin.globalSettings
      val main = project(file("."), "root") settings(MyPlugin.globalSettings:_*) // BAD!
    }

Global settings should *not* be placed into a ``build.sbt`` file.

When overriding global settings, care should be taken to ensure previous
settings from other plugins are not ignored. e.g. when creating a new
``onLoad`` handler, ensure that the previous ``onLoad`` handler is not
removed.

::

    object MyPlugin extends Plugin {
       val globalSettigns: Seq[Setting[_]] = Seq(
         onLoad in Global := (onLoad in Global).value andThen { state =>
             ... return new state ...
         }
       )
     }
