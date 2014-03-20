======================
0.13.2 Migration Guide
======================


Sbt 0.13.2 brings a lot of new rich features with it, however it also represents a shift in some of the standard sbt-isms we've grown
accustomed to.   This guide will walk through the following changes:

* ``AutoPlugin`` / ``AutoImport`` and the deprecation of ``sbt.Plugin`` and ``sbt.Defaults.defaultSettings``
* Incremental task execution using ``.previous``
* Migration to the name-hashing incremental compiler.


Let's start with the possibly breaking (semantic) changes.

Breaking semantic changes
=========================
Sbt has fragmented its core into several plugins which now provide the default settings.  Your build *may* be broken if you
have the following constructs:


Using ``Project(..., settings=???)`` to remove default settings
---------------------------------------------------------------
If your project is removing default settings through the ``settings`` argument, then your build will now also include the
autoPlugin settings *before* your settings.   This means things like ``compile``, ``test`` will still be on your build.

For example a ``project/build.scala`` file like so ::

    import sbt._
    import Keys._

    object MyBuild extends Build {

      def rootSettings = Seq(
        test := (),
        publish := ()
      )

      val root = Project("root", file("."), settings = rootSettings) aggregate(...)

      ...
    }

Will contain the additional settings.   You'll need to explicitly disable the core sbt plugins.  You can
do this by excluding the ``GlobalModule`` plugin which, in term, will prevent any other default plugin
from being loaded.   The new ``project/build.sbt`` ::

    import sbt._
    import Keys._

    object MyBuild extends Build {

      def rootSettings = Seq(
        test := (),
        publish := ()
      )

      val root = Project("root", file("."), settings = rootSettings)
        .aggregate(...)
        .disablePlugins(plugins.GlobalModule)  // Required in 0.13.2 to remove default settings.

      ...
    }


Using ``Project().autoSettings(???)``
-------------------------------------
The ``autoSettings`` feature of projects has been expanded to control *all* settings related to a project.  This means that
settings defined in ``project/*.scala`` files must also be included, as well as those from the default autoplugins.

The follow project definition from a ``project/Build.scala`` will fail in 0.13.2 ::

    import sbt._
    import Keys._
    import AddSettings._

    object MyBuild extends Build {

      def rootSettings = Seq(...)

      val root = Project("root", file("."), settings = rootSettings)
        .autoSettings()
      ...
    }


To fix you need to add both the ``autoPlugins`` and ``projectSettings`` includes as shown ::

    import sbt._
    import Keys._
    import AddSettings._

    object MyBuild extends Build {

      def rootSettings = Seq(...)

      val root = Project("root", file("."), settings = rootSettings)
        .autoSettings(autoPlugins, projectSettings)
      ...
    }

The two new ``AddSettings`` members, ``autoPlugins`` and ``projectSettings`` are required, in the order shown.  For
more information please read :doc:`Setting Initialization <../Architecture/Setting-Initialization>`.

*Note: ``AddSettings`` is still considered an advanced feature added in sbt 0.13.1.  The semantics should not change further in the 0.13.x series, but this is not guaranteed.*



Deprecation Migrations
======================

The addition of auto-plugins has lead to the following two deprecation areas that do not require immediate change, but
should be changed as soon as is practical.

Migrating off ``Defaults.defaultSettings``
------------------------------------------
Prior to sbt 0.13.2, it was common for users to directly use ``Defaults.defaultSettings`` in their projects, as shown 
in the following ``project/build.scala`` file ::


    import sbt._
    import Keys._
    import AddSettings._

    object MyBuild extends Build {

      def projectASettings: Seq[Setting[_]] = 
        Defaults.defaultSettings ++ Seq(...)

      val projectA = Project("a", file("."), settings = projectASettings)
    }

Starting in sbt 0.13.2, all default settings are provided by the core auto-plugins:  ``sbt.plugins.GlobalModule``, ``sbt.plugins.IvyModule`` and ``sbt.plugins.JvmModule``.   Autoplugin settings are injected *before* those configured in ``project/*.scala`` files.
(For details see:  :doc:`Setting Initialization <../Architecture/Setting-Initialization>` ).

This construct will not cause any failures in builds, but does duplicate the default settings in every project and prevents any
auto-plugin attempts to remove settings from working.

To fix, just drop the usage of ``Defaults.defaultSettings``, as shown in the updated ``project/build.scala`` ::

    import sbt._
    import Keys._
    import AddSettings._

    object MyBuild extends Build {

      def projectASettings: Seq[Setting[_]] = Seq(...)

      val projectA = Project("a", file("."), settings = projectASettings)
    }


Migrating off ``sbt.Plugin``
----------------------------
The ``sbt.Plugin`` class has been deprecated in place of three constructs:

1. ``sbt.AutoImport``
2. ``sbt.AutoPlugin``
3. ``sbt.RootPlugin``


Each of these new constructs fills the previous needs of ``sbt.Plugin``, but in a safer, more controlled mechanism.
Let's look at the three use cases of ``sbt.Plugin`` and how they map into the new features.

Providing values that can be directly used in build.sbt
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
One use of ``sbt.Plugin`` was to provide helper methods and setting sequences that can be referenced directly in any ``.sbt`` file.
Starting in sbt 0.13.2, anything which inherits from ``sbt.AutoImport`` will be automatically imported into ``.sbt`` files when they
are compiled.  

Existing plugins, like this one ::

    import sbt._

    object MyPlugin extends Plugin {
      def someSettings: Seq[Setting[_]] = ...
      def helperFunction(...): Stuff = ...
    }

should be modified to just directly extend ``sbt.AutoImport`` ::

    import sbt._
    object MyPlugin extends AutoImport {
      def someSettings: Seq[Setting[_]] = ...
      def helperFunction(...): Stuff = ...
    }

In addition to directly extending ``sbt.AutoImport``, both the ``sbt.AutoPlugin`` and ``sbt.RootPlugin`` classes extend 
``sbt.AutoImport`` which places their methods available in ``.sbt`` files as well.

Many existing sbt plugins are simple libraries of re-usable setting sequences.  Migrating to ``sbt.AutoImport`` is the easiest
path to migrate off of ``sbt.Plugin`` for these libraries.  

.. TODO - Link to AutoPlugin documentation as well as encourage existing plugins to migrate to full AutoPlugin support.


Automatically injecting settings in to all projects/builds.
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Some ``sbt.Plugin`` implementations inject their settings into all projects.  These should instead use the ``sbt.AutoPlugin`` feature to
inject their settings.

The following plugin ::

    import sbt._
    object MyPlugin extends Plugin {
      override def projectSettings = ...
      override def buildSettings = ...
    }

would become ::

    import sbt._
    object MyPlugin extends AutoPlugin {
      // I have no requirements, include me in every project.
      override def select = Plugins.empty
      override def projectSettings = ...
      // These are only added once if the plugin is included on any project.
      override def buildSettings = ...
    }

However, if the plugin relied on ``Default.defaultSettings`` existing on the project, it is highly recommend to depend on the
core plugin which relates to the settings needed.   For example, if the plugin only worked with Ivy-related settings, as
is the case for the sbt-pgp plugin, then it should depend on those ivy settings being included ::

    import sbt._
    object SbtPgp extends AutoPlugin {
      def select = sbt.plugins.IvyModule
      override def projectSettings = ...
      override def buildSettings = ...
    }

This ensures that any project which has explicitly disabled the ``IvyModule`` plugin will not break when the ``SbtPgp`` plugin can't find
the settings it requires.

Sbt 0.13.2 provides the following plugin modules:

* ``sbt.plugins.GlobalModule`` - Global task parallelism settings.
* ``sbt.plugins.IvyModule``    - Settings for resolving/publishing to ivy.  Depends on ``GlobalModule``.
* ``sbt.plugins.JvmModule``    - The remaining sbt default settings for compiling a Scala / Java project.   Depends on ``IvyModule``.


Providing a sequence of settings that users should manually enable
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This is the most common type of plugin, one which just provides a sequence of settings to enable its functionality.  An
example is the sbt-native-packager plugin which provides different types of settings for different artifacts.  Prior to sbt
0.13.2, for example, the sbt-native-packager provides an "archetype" for compiling java servers which is used as follows 
in a ``build.sbt`` ::

    projectArchetypes.java_server

This archetype denotes many layers of settings which need to be included that will allow the packager to generate appropriate
packages for Debian, Red Hat, Windows, etc.   However, it does not allow any downstream sbt plugins to make use of the
knowledge that we included the ``java_server`` settings vs. any other set of sbt-native-packager settings.   

Some plugins would provide a setting which contains this information, something like ::

    val archetype = settingKey[String]("The selected project archetype used for packaging, examples 'Server', 'Application'")
    
    archetype := "Server"

Then, dependent plugins would have convolute settings to read this property and take appropriate action.

In sbt 0.13.2 the ``sbt.RootPlugin`` allows us to specify this information directly in the plugin model.   A "root" plugin is one which
must be explicitly enabled on a project, *but* which other plugins can depend on to automatically inject their settings.

Here's an example series of plugins which are all enabled by a "root" ``JavaServer`` plugin ::

    import sbt._

    object JavaServer extends RootPlugin {
      override val projectSettings = ...
    }

    object MyCompaniesRpmSettings extends AutoPlugin {
      def select = JavaServer
      override val projectSettings =
        Seq(
          rpmLicense := ...,
          ...
        )
    }

In the above, the JavaServer settings must be manually enabled.  Once enabled, the company-specific AutoPlugin will attach its
settings to the project *after* the ``RootPlugin`` settings.   A user's ``build.sbt`` file would look as follows ::

    val myAwesomeWebProject = project.in(file(".")).addPlugins(JavaServer)

This should enable more seamless integration between plugins than existed before in the sbt ecosystem.  All plugins are encouraged
to provide accurate ``select`` implementations and ``RootPlugin`` instances which others can depend on.  Previously,
all plugins avoided automatically adding settings because it left no control for users of which plugins could be added.  Now,
user can explicitly disable any plugin on a project using the ``removePlugins`` method.  Here's an example where
we remove the specific ``MyCompaniesRpmSettings`` from our project ::

    val myAwesomeWebProject = project.in(file(".")).addPlugins(JavaServer).removePlugins(MyCompaniesRpmSettings)


.. TODO - Changes to plugin best practices section (or reformating the above).


Incremental Tasks
=================
sbt has always been designed with incremental execution in mind.  In the past, it was common convention to manually write/read task
output to the filesystem before deciding if a task needs to be run.  For example ::

    myTask := {
      tryReadLastValue() match {
        case Some(lastValue) if !changed(lastValue) => lastValue
        case _ => 
          val result = myTaskImpl()
          saveLastValue(result)
          result
      }
    }

sbt now provides a ``.previous`` method available on all ``TaskKey`` instances which will automatically store computed results and
attempt to read them from disk for you.  The above code becomes ::

    myTask := {
       myTask.previous match {
         case Some(lastValue) if !changed(value) => lastValue
         case => myTaskImpl()
       }
    }

The change detection for incremental tasks is left to the implementer, as change detection is usually specific to the task itself.

In addition, to use ``.previous`` a task implementer must also provide a means of serializing the values for sbt.  This is done through
the sbinary library, which allows you to specify a binary serialization for classes.  Here's a complete, but trivial ``.previous`` example ::

    import sbinary.DefaultProtocol._
    import sbinary._

    case class MyTaskResult(data: String)

    val myTask = taskKey[MyTaskResult]("a useless example task.")

    myTask := {
      implicit object MyFormat extends Format[MyTaskResult] {
        def reads(in: Input): MyTaskResult = MyTaskResult(read[String](in))
        def writes(out: Output, value: MyTaskResult) = write(out, value.data)
      }
      myTask.previous match {
        case Some(value) => value
        case _ => MyTaskResult("test")
      }

    }

