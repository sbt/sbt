=============
Using Plugins
=============

Please read the earlier pages in the Getting Started Guide first, in
particular you need to understand :doc:`build.sbt <Basic-Def>`,
:doc:`library dependencies <Library-Dependencies>`,
and :doc:`.scala build definition <Full-Def>` before reading
this page.

What is a plugin?
-----------------

A plugin extends the build definition, most commonly by adding new
settings. The new settings could be new tasks. For example, a plugin
could add a ``code-coverage`` task which would generate a test coverage
report.

Adding a plugin
---------------

The short answer
~~~~~~~~~~~~~~~~

If your project is in directory ``hello``, edit
``hello/project/build.sbt`` and add the plugin location as a resolver,
then call ``addSbtPlugin`` with the plugin's Ivy module ID:

::

    resolvers += Classpaths.typesafeResolver

    addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.0.0")

If the plugin were located on one of the default repositories, you
wouldn't have to add a resolver, of course.

Global plugins
~~~~~~~~~~~~~~

Plugins can be installed for all your projects at once by dropping them
in ``~/.sbt/plugins/``. ``~/.sbt/plugins/`` is an sbt project whose
classpath is exported to all sbt build definition projects. Roughly
speaking, any ``.sbt`` files in ``~/.sbt/plugins/`` behave as if they
were in the ``project/`` directory for all projects, and any ``.scala``
files in ``~/.sbt/plugins/project/`` behave as if they were in the
``project/project/`` directory for all projects.

You can create ``~/.sbt/plugins/build.sbt`` and put ``addSbtPlugin()``
expressions in there to add plugins to all your projects at once.

How it works
~~~~~~~~~~~~

Be sure you understand the :doc:`recursive nature of sbt projects <Full-Def>`
described earlier and how to add a :doc:`managed dependency <Library-Dependencies>`.

Dependencies for the build definition
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Adding a plugin means *adding a library dependency to the build
definition*. To do that, you edit the build definition for the build
definition.

Recall that for a project ``hello``, its build definition project lives
in ``hello/*.sbt`` and ``hello/project/*.scala``:

.. code-block:: text


       hello/                  # your project's base directory

           build.sbt           # build.sbt is part of the source code for the
                               #   build definition project inside project/

           project/            # base directory of the build definition project

               Build.scala     # a source file in the project/ project,
                               #   that is, a source file in the build definition

If you wanted to add a managed dependency to project ``hello``, you
would add to the ``libraryDependencies`` setting either in
``hello/*.sbt`` or ``hello/project/*.scala``.

You could add this in ``hello/build.sbt``:

::

    libraryDependencies += "org.apache.derby" % "derby" % "10.4.1.3" % "test"

If you add that and start up the sbt interactive mode and type
``show dependencyClasspath``, you should see the derby jar on your
classpath.

To add a plugin, do the same thing but recursed one level. We want the
*build definition project* to have a new dependency. That means changing
the ``libraryDependencies`` setting for the build definition of the
build definition.

The build definition of the build definition, if your project is
``hello``, would be in ``hello/project/*.sbt`` and
``hello/project/project/*.scala``.

The simplest "plugin" has no special sbt support; it's just a jar file.
For example, edit ``hello/project/build.sbt`` and add this line:

::

    libraryDependencies += "net.liftweb" % "lift-json" % "2.0"

Now, at the sbt interactive prompt, ``reload plugins`` to enter the
build definition project, and try ``show dependencyClasspath``. You
should see the lift-json jar on the classpath. This means: you could use
classes from lift-json in your ``Build.scala`` or ``build.sbt`` to
implement a task. You could parse a JSON file and generate other files
based on it, for example. Remember, use ``reload return`` to leave the
build definition project and go back to the parent project.

(Stupid sbt trick: type ``reload plugins`` over and over. You'll find
yourself in the project rooted in
``project/project/project/project/project/project/``. Don't worry, it
isn't useful. Also, it creates ``target`` directories all the way down,
which you'll have to clean up.)

``addSbtPlugin``
^^^^^^^^^^^^^^^^

``addSbtPlugin`` is just a convenience method. Here's its definition:

::

    def addSbtPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
      libraryDependencies +=
        sbtPluginExtra(dependency, (sbtVersion in update).value, scalaVersion.value)

The appended dependency is based on ``sbtVersion in update``
(sbt's version scoped to the ``update`` task) and ``scalaVersion`` (the
version of scala used to compile the project, in this case used to
compile the build definition). ``sbtPluginExtra`` adds the sbt and Scala
version information to the module ID.

``plugins.sbt``
^^^^^^^^^^^^^^^

Some people like to list plugin dependencies (for a project ``hello``)
in ``hello/project/plugins.sbt`` to avoid confusion with
``hello/build.sbt``. sbt does not care what ``.sbt`` files are called,
so both ``build.sbt`` and ``project/plugins.sbt`` are conventions. sbt
*does* of course care where the sbt files are *located*. ``hello/*.sbt``
would contain dependencies for ``hello`` and ``hello/project/*.sbt``
would contain dependencies for ``hello``'s build definition.

Plugins can add settings and imports automatically
--------------------------------------------------

In one sense a plugin is just a jar added to ``libraryDependencies`` for
the build definition; you can then use the jar from build definition
code as in the lift-json example above.

However, jars intended for use as sbt plugins can do more.

If you download a plugin jar (`here's one for
sbteclipse <http://repo.typesafe.com/typesafe/ivy-releases/com.typesafe.sbteclipse/sbteclipse/scala_2.9.1/sbt_0.11.0/1.4.0/jars/sbteclipse.jar>`_)
and unpack it with ``jar xf``, you'll see that it contains a text file
``sbt/sbt.plugins``. In ``sbt/sbt.plugins`` there's an object name on
each line like this:

.. code-block:: text

    com.typesafe.sbteclipse.SbtEclipsePlugin

``com.typesafe.sbteclipse.SbtEclipsePlugin`` is the name of an object
that extends ``sbt.Plugin``. The ``sbt.Plugin`` trait is very simple:

::

    trait Plugin {
      def settings: Seq[Setting[_]] = Nil
    }

sbt looks for objects listed in ``sbt/sbt.plugins``. When it finds
``com.typesafe.sbteclipse.SbtEclipsePlugin``, it adds
``com.typesafe.sbteclipse.SbtEclipsePlugin.settings`` to the settings
for the project. It also does
``import com.typesafe.sbteclipse.SbtEclipsePlugin._`` for any ``.sbt``
files, allowing a plugin to provide values, objects, and methods to
``.sbt`` files in the build definition.

Adding settings manually from a plugin
--------------------------------------

If a plugin defines settings in the ``settings`` field of a ``Plugin``
object, you don't have to do anything to add them.

However, plugins often avoid this because you could not control which
projects in a :doc:`multi-project build <Multi-Project>` would use the plugin.

A whole batch of settings can be added by directly referencing the sequence of settings in a `build.sbt` file. So, if a plugin has something like this:

::

    object MyPlugin extends Plugin {
       val myPluginSettings = Seq(settings in here)
    }

You could add all those settings in ``build.sbt`` with this syntax:

::

    myPluginSettings

Creating a plugin
-----------------

After reading this far, you pretty much know how to *create* an sbt
plugin as well. There's one trick to know; set ``sbtPlugin := true`` in
``build.sbt``. If ``sbtPlugin`` is true, the project will scan its
compiled classes for instances of ``Plugin``, and list them in
``sbt/sbt.plugins`` when it packages a jar. ``sbtPlugin := true`` also
adds sbt to the project's classpath, so you can use sbt APIs to
implement your plugin.

Learn more about creating a plugin at :doc:`/Extending/Plugins`
and :doc:`/Extending/Plugins-Best-Practices`.

Available Plugins
-----------------

There's :doc:`a list of available plugins </Community/Community-Plugins>`.

Some especially popular plugins are:

-  those for IDEs (to import an sbt project into your IDE)
-  those supporting web frameworks, such as
   `xsbt-web-plugin <https://github.com/JamesEarlDouglas/xsbt-web-plugin>`_.

:doc:`Check out the list</Community/Community-Plugins>`.

Next
----

Move on to :doc:`multi-project builds <Multi-Project>`.
