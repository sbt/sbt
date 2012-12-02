Frequently Asked Questions
==========================

Project Information
-------------------

How do I get help?
~~~~~~~~~~~~~~~~~~

Please use the `mailing list`_ for questions, comments, and discussions.

-  Please state the problem or question clearly and provide enough
   context. Code examples and build transcripts are often useful when
   appropriately edited.
-  Providing small, reproducible examples are a good way to get help
   quickly.
-  Include relevant information such as the version of sbt and Scala
   being used.

How do I report a bug?
~~~~~~~~~~~~~~~~~~~~~~

Please use the `issue tracker <https://github.com/harrah/xsbt/issues>`_
to report confirmed bugs. Do not use it to ask questions. If you are
uncertain whether something is a bug, please ask on the `mailing list`_ first.

How can I help?
~~~~~~~~~~~~~~~

-  Fix mistakes that you notice on the wiki.
-  Make `bug reports <https://github.com/harrah/xsbt/issues>`_ that are
   clear and reproducible.
-  Answer questions on the `mailing list`_.
-  Fix issues that affect you. `Fork, fix, and submit a pull
   request <http://help.github.com/fork-a-repo/>`_.
-  Implement features that are important to you. There is an
   :doc:`/Community/Opportunities` page for some ideas, but the most useful
   contributions are usually ones you want yourself.

For more details on developing sbt, see
`Developing.pdf <http://harrah.github.com/xsbt/Developing.pdf>`_

0.7 to 0.10+ Migration
----------------------

How do I migrate from 0.7 to 0.10+?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

See the :doc:`migration page </Detailed-Topics/Migrating-from-sbt-0.7.x-to-0.10.x>` first and
then the following questions.

Where has 0.7's ``lib_managed`` gone?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, sbt |version| loads managed libraries from your ivy cache without
copying them to a ``lib_managed`` directory. This fixes some bugs with
the previous solution and keeps your project directory small. If you
want to insulate your builds from the ivy cache being cleared, set
``retrieveManaged := true`` and the dependencies will be copied to
``lib_managed`` as a build-local cache (while avoiding the issues of
``lib_managed`` in 0.7.x).

This does mean that existing solutions for sharing libraries with your
favoured IDE may not work. There are |version| plugins for IDEs being
developed:

-  IntelliJ IDEA: [[https://github.com/mpeltonen/sbt-idea]]
-  Netbeans: [[https://github.com/remeniuk/sbt-netbeans-plugin]]
-  Eclipse: [[https://github.com/typesafehub/sbteclipse]]

What are the commands I can use in |version| vs. 0.7?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For a list of commands, run ``help``. For details on a specific command,
run ``help <command>``. To view a list of tasks defined on the current
project, run ``tasks``. Alternatively, see the :doc:`Running </Getting-Started/Running>`
page in the Getting Started Guide for descriptions of common commands and tasks.

If in doubt start by just trying the old command as it may just work.
The built in TAB completion will also assist you, so you can just press
TAB at the beginning of a line and see what you get.

The following commands work pretty much as in 0.7 out of the box:

.. code-block:: text

    reload
    update
    compile
    test
    testOnly
    publishLocal
    exit

Why have the resolved dependencies in a multi-module project changed since 0.7?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

sbt 0.10 fixes a flaw in how dependencies get resolved in multi-module
projects. This change ensures that only one version of a library appears
on a classpath.

Use ``last update`` to view the debugging output for the last ``update``
run. Use ``show update`` to view a summary of files comprising managed
classpaths.

My tests all run really fast but some are broken that weren't in 0.7!
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Be aware that compilation and tests run in parallel by default in sbt
|version|. If your test code isn't thread-safe then you may want to change
this behaviour by adding one of the following to your ``build.sbt``:

::

    // Execute tests in the current project serially.
    // Tests from other projects may still run concurrently.
    parallelExecution in Test := false

    // Execute everything serially (including compilation and tests)
    parallelExecution := false

How do I set log levels in |version| vs. 0.7?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``warn``, ``info``, ``debug`` and ``error`` don't work any more.

The new syntax in the sbt |version| shell is:
``text > set logLevel := Level.Warn``

Or in your ``build.sbt`` file write:

::

    logLevel := Level.Warn

What happened to the web development and Web Start support since 0.7?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Web application support was split out into a plugin. See the
`xsbt-web-plugin <https://github.com/JamesEarlDouglas/xsbt-web-plugin>`_ project.

For an early version of an xsbt Web Start plugin, visit the
`xsbt-webstart <https://github.com/ritschwumm/xsbt-webstart>`_ project.

How are inter-project dependencies different in |version| vs. 0.7?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In |version|, there are three types of project dependencies (classpath,
execution, and configuration) and they are independently defined. These
were combined in a single dependency type in 0.7.x. A declaration like:

::

    lazy val a = project("a", "A")
    lazy val b = project("b", "B", a)

meant that the ``B`` project had a classpath and execution dependency on
``A`` and ``A`` had a configuration dependency on ``B``. Specifically,
in 0.7.x:

1. Classpath: Classpaths for ``A`` were available on the appropriate
   classpath for ``B``.
2. Execution: A task executed on ``B`` would be executed on ``A`` first.
3. Configuration: For some settings, if they were not overridden in
   ``A``, they would default to the value provided in ``B``.

In |version|, declare the specific type of dependency you want. Read about
:doc:`multi-project builds </Getting-Started/Multi-Project>` in the Getting
Started Guide for details.

Where did class/object X go since 0.7?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

================================================================================================================================================================================================ =====================================================================================================================================================================================
0.7                                                                                                                                                                                              |version|
================================================================================================================================================================================================ =====================================================================================================================================================================================
| `FileUtilities <http://simple-build-tool.googlecode.com/svn/artifacts/latest/api/sbt/FileUtilities$object.html>`_                                                                              `IO <../api/sbt/IO$.html>`_
`Path class <http://simple-build-tool.googlecode.com/svn/artifacts/latest/api/sbt/Path.html>`_ and `object <http://simple-build-tool.googlecode.com/svn/artifacts/latest/api/sbt/Path$.html>`_   `Path object <../api/sbt/Path$.html>`_, ``File``, `RichFile <../api/sbt/RichFile.html>`_
`PathFinder class <http://simple-build-tool.googlecode.com/svn/artifacts/latest/api/sbt/PathFinder.html>`_                                                                                       ``Seq[File]``, `PathFinder class <../api/sbt/PathFinder.html>`_, `PathFinder object <../api/sbt/PathFinder$.html>`_
================================================================================================================================================================================================ =====================================================================================================================================================================================


Where can I find plugins for |version|?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

See :doc:`/Community/Community-Plugins` for a list of currently available plugins.

Usage
-----

My last command didn't work but I can't see an explanation. Why?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

sbt |version| by default suppresses most stack traces and debugging
information. It has the nice side effect of giving you less noise on
screen, but as a newcomer it can leave you lost for explanation. To see
the previous output of a command at a higher verbosity, type
``last <task>`` where ``<task>`` is the task that failed or that you
want to view detailed output for. For example, if you find that your
``update`` fails to load all the dependencies as you expect you can
enter:

.. code-block:: console

    > last update

and it will display the full output from the last run of the ``update``
command.

How do I disable ansi codes in the output?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Sometimes sbt doesn't detect that ansi codes aren't supported and you
get output that looks like:

.. code-block:: text

      [0m[ [0minfo [0m]  [0mSet current project to root

or ansi codes are supported but you want to disable colored output. To
completely disable ansi codes, set the ``sbt.log.noformat`` system
property to ``true``. For example,

.. code-block :: console

    $ sbt -Dsbt.log.noformat=true

How can I start a Scala interpreter (REPL) with sbt project configuration (dependencies, etc.)?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You may run ``sbt console``.

Build definitions
-----------------

What are the ``:=``, ``+=``, ``++=```, and ``~=`` methods?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

These are methods on keys used to construct a ``Setting``. The Getting
Started Guide covers all these methods, see :doc:`.sbt build definition </Getting-Started/Basic-Def>`
and :doc:`more about settings </Getting-Started/More-About-Settings>` for example.

What is the ``%`` method?
~~~~~~~~~~~~~~~~~~~~~~~~~

It's used to create a ``ModuleID`` from strings, when specifying managed
dependencies. Read the Getting Started Guide about
:doc:`library dependencies </Getting-Started/Library-Dependencies>`.

What is ``ModuleID``, ``Project``, ...?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To figure out an unknown type or method, have a look at the
:doc:`Getting Started Guide </Getting-Started/Welcome>` if you have not.
Also try the :doc:`index </Name-Index>` of commonly used methods, values, and types,
the `API Documentation <../api/index>`_ and the
`hyperlinked sources <../sxr/index>`_.

How do I add files to a jar package?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The files included in an artifact are configured by default by a task
``mappings`` that is scoped by the relevant package task. The
``mappings`` task returns a sequence ``Seq[(File,String)]`` of mappings
from the file to include to the path within the jar. See
:doc:`/Detailed-Topics/Mapping-Files` for details on creating these mappings.

For example, to add generated sources to the packaged source artifact:

::

    mappings in (Compile, packageSrc) <++=
      (sourceManaged in Compile, managedSources in Compile) map { (base, srcs) =>
          import Path.{flat, relativeTo}
        srcs x (relativeTo(base) | flat)
      }

This takes sources from the ``managedSources`` task and relativizes them
against the ``managedSource`` base directory, falling back to a
flattened mapping. If a source generation task doesn't write the sources
to the ``managedSource`` directory, the mapping function would have to
be adjusted to try relativizing against additional directories or
something more appropriate for the generator.

.. _generate-sources-resources:

How can I generate source code or resources?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

sbt provides standard hooks for adding source or resource generation
tasks. A generation task should generate sources in a subdirectory of
``sourceManaged`` for sources or ``resourceManaged`` for resources and
return a sequence of files generated. The key to add the task to is
called ``sourceGenerators`` for sources and ``resourceGenerators`` for
resources. It should be scoped according to whether the generated files
are main (``Compile``) or test (``Test``) sources or resources. This
basic structure looks like:

::

    sourceGenerators in Compile += <your Task[Seq[File]] here>

For example, assuming a method
``def makeSomeSources(base: File): Seq[File]``,

::

    sourceGenerators in Compile += Def.task {
      makeSomeSources( (sourceManaged in Compile).value / "demo")
    }

As a specific example, the following generates a hello world source
file:

::

    sourceGenerators in Compile += Def.task {
      val file = (sourceManaged in Compile) / "demo" / "Test.scala"
      IO.write(file, """object Test extends App { println("Hi") }""")
      Seq(file)
    }

Executing 'run' will print "Hi". Change ``Compile`` to ``Test`` to make
it a test source. To generate resources, change ``sourceGenerators`` to
``resourceGenerators`` and ``sourceManaged`` to ``resourceManaged``.
Normally, you would only want to generate sources when necessary and not
every run.

By default, generated sources and resources are not included in the
packaged source artifact. To do so, add them as you would other
mappings. See the ``Adding files to a package`` section.

How can a task avoid redoing work if the input files are unchanged?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

There is basic support for only doing work when input files have changed
or when the outputs haven't been generated yet. This support is
primitive and subject to change.

The relevant methods are two overloaded methods called
`FileFunction.cached <../api/sbt/FileFunction$.html>`_.
Each requires a directory in which to store cached data. Sample usage
is:

::

    // define a task that takes some inputs
    //   and generates files in an output directory
    myTask := {
        // wraps a function taskImpl in an uptodate check
        //   taskImpl takes the input files, the output directory,
        //   generates the output files and returns the set of generated files
        val cachedFun = FileFunction.cached(cacheDirectory.value / "my-task") { (in: Set[File]) =>
          taskImpl(in, target.value) : Set[File]
        }
        // Applies the cached function to the inputs files
        cachedFun(inputs.value)
    }

There are two additional arguments for the first parameter list that
allow the file tracking style to be explicitly specified. By default,
the input tracking style is ``FilesInfo.lastModified``, based on a
file's last modified time, and the output tracking style is
``FilesInfo.exists``, based only on whether the file exists. The other
available style is ``FilesInfo.hash``, which tracks a file based on a
hash of its contents. See the `FilesInfo
API <../api/sbt/FilesInfo$.html>`_ for
details.

A more advanced version of ``FileFunction.cached`` passes a data
structure of type
`ChangeReport <../api/sbt/ChangeReport.html>`_
describing the changes to input and output files since the last
evaluation. This version of ``cached`` also expects the set of files
generated as output to be the result of the evaluated function.

Extending sbt
-------------

How can I add a new configuration?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The following example demonstrates adding a new set of compilation
settings and tasks to a new configuration called ``samples``. The
sources for this configuration go in ``src/samples/scala/``. Unspecified
settings delegate to those defined for the ``compile`` configuration.
For example, if ``scalacOptions`` are not overridden for ``samples``,
the options for the main sources are used.

Options specific to ``samples`` may be declared like:

::

    scalacOptions in Samples += "-deprecation"

This uses the main options as base options because of ``+=``. Use ``:=``
to ignore the main options:

::

    scalacOptions in Samples := "-deprecation" :: Nil

The example adds all of the usual compilation related settings and tasks
to ``samples``:

::

    samples:run
    samples:runMain
    samples:compile
    samples:console
    samples:consoleQuick
    samples:scalacOptions
    samples:fullClasspath
    samples:package
    samples:packageSrc
    ...


How do I add a test configuration?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

See the ``Additional test configurations`` section of :doc`/Detailed-Topics/Testing`.

How can I create a custom run task, in addition to ``run``?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This answer is extracted from a `mailing list
discussion <http://groups.google.com/group/simple-build-tool/browse_thread/thread/4c28ee5b7e18b46a/>`_.

Read the Getting Started Guide up to :doc:`custom settings </Getting-Started/Custom-Settings>` for background.

A basic run task is created by:

::

      // this lazy val has to go in a full configuration
      lazy val myRunTask = TaskKey[Unit]("myRunTask")

      // this can go either in a `build.sbt` or the settings member
      //   of a Project in a full configuration
      fullRunTask(myRunTask, Test, "foo.Foo", "arg1", "arg2")

or, if you really want to define it inline (as in a basic ``build.sbt``
file):

::

       fullRunTask(TaskKey[Unit]("myRunTask"), Test, "foo.Foo", "arg1", "arg2")

If you want to be able to supply arguments on the command line, replace
``TaskKey`` with ``InputKey`` and ``fullRunTask`` with
``fullRunInputTask``. The ``Test`` part can be replaced with another
configuration, such as ``Compile``, to use that configuration's
classpath.

This run task can be configured individually by specifying the task key
in the scope. For example:

::

    fork in myRunTask := true

    javaOptions in myRunTask += "-Xmx6144m"

How can I delegate settings from one task to another task?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Settings :doc:`scoped </Getting-Started/Scopes>` to one task can fall back to
another task if undefined in the first task. This is called delegation.

The following key definitions specify that settings for ``myRun``
delegate to ``aRun``

::

    val aRun = TaskKey[Unit]("aRun", "A run task.")

    //   The last parameter to TaskKey.apply here is a repeated one
    val myRun = TaskKey[Unit]("myRun", "Custom run task.", aRun)

In use, this looks like:

::

    // Make the run task as before.
    fullRunTask(myRun, Compile, "pkg.Main", "arg1", "arg2")

    // If fork in myRun is not explicitly set,
    //   then this also configures myRun to fork.
    // If fork in myRun is set, it overrides this setting
    //   because it is more specific.
    fork in aRun := true

    // Appends "-Xmx2G" to the current options for myRun.
    //   Because we haven't defined them explicitly,
    //   the current options are delegated to aRun.
    //   So, this says to use the same options as aRun
    //   plus -Xmx2G.
    javaOptions in myRun += "-Xmx2G"

How should I express a dependency on an outside tool such as proguard?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Tool dependencies are used to implement a task and are not needed by
project source code. These dependencies can be declared in their own
configuration and classpaths. These are the steps:

1. Define a new :doc:`configuration </Dormant/Configurations>`.
2. Declare the tool :doc:`dependencies </Detailed-Topics/Library-Management>` in that
   configuration.
3. Define a classpath that pulls the dependencies from the :doc:`/Detailed-Topics/Update-Report` produced by ``update``.
4. Use the classpath to implement the task.

As an example, consider a ``proguard`` task. This task needs the
ProGuard jars in order to run the tool. Assuming a new configuration
defined in the full build definition (#1):

::

    val ProguardConfig = config("proguard") hide

the following are settings that implement #2-#4:

::

    // Add proguard as a dependency in the custom configuration.
    //  This keeps it separate from project dependencies.
    libraryDependencies +=
       "net.sf.proguard" % "proguard" % "4.4" % ProguardConfig.name

    // Extract the dependencies from the UpdateReport.
    managedClasspath in proguard :=
        Classpaths.managedJars(proguardConfig, (classpathTypes in proguard).value, update.value)
    }

    // Use the dependencies in a task, typically by putting them
    //  in a ClassLoader and reflectively calling an appropriate
    //  method.
    proguard := {
	    val cp: Seq[File] = (managedClasspath in proguard).value
      // ... do something with , which includes proguard ...
    }

How would I change sbt's classpath dynamically?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

It is possible to register additional jars that will be placed on sbt's
classpath (since version 0.10.1). Through
`State <../api/sbt/State$.html>`_, it
is possible to obtain a
`xsbti.ComponentProvider <../api/xsbti/ComponentProvider.html>`_,
which manages application components. Components are groups of files in
the ``~/.sbt/boot/`` directory and, in this case, the application is
sbt. In addition to the base classpath, components in the "extra"
component are included on sbt's classpath.

(Note: the additional components on an application's classpath are
declared by the ``components`` property in the ``[main]`` section of the
launcher configuration file ``boot.properties``.)

Because these components are added to the ``~/.sbt/boot/`` directory and
``~/.sbt/boot/`` may be read-only, this can fail. In this case, the user
has generally intentionally set sbt up this way, so error recovery is
not typically necessary (just a short error message explaining the
situation.)

Example of dynamic classpath augmentation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following code can be used where a ``State => State`` is required,
such as in the ``onLoad`` setting (described below) or in a
:doc:`command </Extending/Commands>`. It adds some files to the "extra" component and
reloads sbt if they were not already added. Note that reloading will
drop the user's session state.

::

    def augment(extra: Seq[File])(s: State): State =
    {
        // Get the component provider
      val cs: xsbti.ComponentProvider = s.configuration.provider.components()

        // Adds the files in 'extra' to the "extra" component
        //   under an exclusive machine-wide lock.
        //   The returned value is 'true' if files were actually copied and 'false'
        //   if the target files already exists (based on name only).
      val copied: Boolean = s.locked(cs.lockFile, cs.addToComponent("extra", extra.toArray))

        // If files were copied, reload so that we use the new classpath.
      if(copied) s.reload else s
    }

How can I take action when the project is loaded or unloaded?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The single, global setting ``onLoad`` is of type ``State => State`` (see
:doc:`/Extending/Build-State`) and is executed once, after all projects are built and
loaded. There is a similar hook ``onUnload`` for when a project is
unloaded. Project unloading typically occurs as a result of a ``reload``
command or a ``set`` command. Because the ``onLoad`` and ``onUnload``
hooks are global, modifying this setting typically involves composing a
new function with the previous value. The following example shows the
basic structure of defining ``onLoad``:

::

    // Compose our new function 'f' with the existing transformation.
    {
      val f: State => State = ...
      onLoad in Global ~= (f compose _)
    }

Example of project load/unload hooks
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following example maintains a count of the number of times a project
has been loaded and prints that number:

::

    {
      // the key for the current count
      val key = AttributeKey[Int]("loadCount")
      // the State transformer
      val f = (s: State) => {
        val previous = s get key getOrElse 0
        println("Project load count: " + previous)
        s.put(key, previous + 1)
      }
      onLoad in Global ~= (f compose _)
    }

Errors
------

On project load, "Reference to uninitialized setting"
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Setting initializers are executed in order. If the initialization of a
setting depends on other settings that has not been initialized, sbt
will stop loading.

In this example, we try to append a library to ``libraryDependencies``
before it is initialized with an empty sequence.

::

    object MyBuild extends Build {
      val root = Project(id = "root", base = file("."),
        settings = Seq(
          libraryDependencies += "commons-io" % "commons-io" % "1.4" % "test"
        )
      )
    }

To correct this, include the default settings, which includes
``libraryDependencies := Seq()``.

::

    settings = Defaults.defaultSettings ++ Seq(
      libraryDependencies += "commons-io" % "commons-io" % "1.4" % "test"
    )

A more subtle variation of this error occurs when using :doc:`scoped settings </Getting-Started/Scopes>`.

::

    // error: Reference to uninitialized setting
    settings = Defaults.defaultSettings ++ Seq(
      libraryDependencies += "commons-io" % "commons-io" % "1.2" % "test",
      fullClasspath ~= (_.filterNot(_.data.name.contains("commons-io")))
    )

Generally, all of the setting definition methods can be expressed in terms of
``:=``. To better understand the error, we can rewrite the setting as:

::

    // error: Reference to uninitialized setting
    fullClasspath := fullClasspath.value.filterNot(_.data.name.contains("commons-io"))

This setting varies between the test and compile scopes. The solution is
use the scoped setting, both as the input to the initializer, and the
setting that we update.

::

    fullClasspath in Compile := (fullClasspath in Compile).value.filterNot(_.data.name.contains("commons-io"))

    // or equivalently
    fullClasspath in Compile ~= (_.filterNot(_.data.name.contains("commons-io")))

Dependency Management
---------------------

How do I resolve a checksum error?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This error occurs when the published checksum, such as a sha1 or md5
hash, differs from the checksum computed for a downloaded artifact, such
as a jar or pom.xml. An example of such an error is:

::

    [warn]  problem while downloading module descriptor:
    http://repo1.maven.org/maven2/commons-fileupload/commons-fileupload/1.2.2/commons-fileupload-1.2.2.pom: 
    invalid sha1: expected=ad3fda4adc95eb0d061341228cc94845ddb9a6fe computed=0ce5d4a03b07c8b00ab60252e5cacdc708a4e6d8 (1070ms) 

The invalid checksum should generally be reported to the repository
owner (as `was done <https://issues.sonatype.org/browse/MVNCENTRAL-46>`_
for the above error). In the meantime, you can temporarily disable
checking with the following setting:

::

    checksums in update := Nil

See :doc:`/Detailed-Topics/Library-Management` for details.

I've added a plugin, and now my cross-compilations fail!
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This problem crops up frequently. Plugins are only published for the
Scala version that sbt uses (currently, 2.9.1). You can still *use*
plugins during cross-compilation, because sbt only looks for a 2.9.1
version of the plugin.

**... unless you specify the plugin in the wrong place!**

A typical mistake is to put global plugin definitions in
``~/.sbt/plugins.sbt``. **THIS IS WRONG.** ``.sbt`` files in ``~/.sbt``
are loaded for *each* build--that is, for *each* cross-compilation. So,
if you build for Scala 2.9.0, sbt will try to find a version of the
plugin that's compiled for 2.9.0--and it usually won't. That's because
it doesn't *know* the dependency is a plugin.

To tell sbt that the dependency is an sbt plugin, make sure you define
your global plugins in a ``.sbt`` file in ``~/.sbt/plugins/``. sbt knows
that files in ``~/.sbt/plugins`` are only to be used by sbt itself, not
as part of the general build definition. If you define your plugins in a
file under *that* directory, they won't foul up your cross-compilations.
Any file name ending in ``.sbt`` will do, but most people use
``~/.sbt/plugins/build.sbt`` or ``~/.sbt/plugins/plugins.sbt``. ##
Miscellaneous

How do I use the Scala interpreter in my code?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

sbt runs tests in the same JVM as sbt itself and Scala classes are not
in the same class loader as the application classes. Therefore, when
using the Scala interpreter, it is important to set it up properly to
avoid an error message like:

::

     Failed to initialize compiler: class scala.runtime.VolatileBooleanRef not found.
     ** Note that as of 2.8 scala does not assume use of the java classpath.
     ** For the old behavior pass -usejavacp to scala, or if using a Settings
     ** object programmatically, settings.usejavacp.value = true.

The key is to initialize the Settings for the interpreter using
*embeddedDefaults*. For example:

::

     val settings = new Settings
     settings.embeddedDefaults[MyType]
     val interpreter = new Interpreter(settings, ...)

Here, MyType is a representative class that should be included on the
interpreter's classpath and in its application class loader. For more
background, see the `original
proposal <https://gist.github.com/404272>`_ that resulted in
*embeddedDefaults* being added.

Similarly, use a representative class as the type argument when using
the *break* and *breakIf* methods of *ILoop*, as in the following
example:

::

      def x(a: Int, b: Int) = {
        import scala.tools.nsc.interpreter.ILoop
        ILoop.breakIf[MyType](a != b, "a" -> a, "b" -> b )
      }

