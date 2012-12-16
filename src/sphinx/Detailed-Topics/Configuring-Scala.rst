=================
Configuring Scala
=================

sbt needs to obtain Scala for a project and it can do this automatically or you can configure it explicitly.
The Scala version that is configured for a project will compile, run, document, and provide a REPL for the project code.
When compiling a project, sbt needs to run the Scala compiler as well as provide the compiler with a classpath, which may include several Scala jars, like the reflection jar.

Automatically managed Scala
===========================

The most common case is when you want to use a version of Scala that is available in a repository.
The only required configuration is the Scala version you want to use.
For example,

::

    scalaVersion := "2.10.0"

This will retrieve Scala from the repositories configured via the  ``resolvers`` setting.
It will use this version for building your project: compiling, running, scaladoc, and the REPL.

Configuring the scala-library dependency
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, the standard Scala library is automatically added as a dependency.
If you want to configure it differently than the default or you have a project with only Java sources, set:

::

    autoScalaLibrary := false

In order to compile Scala sources, the Scala library needs to be on the classpath.
When ``autoScalaLibrary`` is true, the Scala library will be on all classpaths: test, runtime, and compile.
Otherwise, you need to add it like any other dependency.
For example, the following dependency definition uses Scala only for tests:

::

    autoScalaLibrary := false

    libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value % "test"

Configuring additional Scala dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When using a Scala dependency other than the standard library, add it as a normal managed dependency.
For example, to depend on the Scala compiler,

::

    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value

Note that this is necessary regardless of the value of the ``autoScalaLibrary`` setting described in the previous section.

Configuring Scala tool dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In order to compile Scala code, run scaladoc, and provide a Scala REPL, sbt needs the ``scala-compiler`` jar.
This should not be a normal dependency of the project, so sbt adds a dependency on ``scala-compiler`` in the special, private ``scala-tool`` configuration.
It may be desirable to have more control over this in some situations.
Disable this automatic behavior with the ``autoScalaInstance`` key:

::

    managedScalaInstance := false

This will also disable the automatic dependency on ``scala-library``.
If you do not need the Scala compiler for anything (compiling, the REPL, scaladoc, etc...), you can stop here.
sbt does not need an instance of Scala for your project in that case.
Otherwise, sbt will still need access to the jars for the Scala compiler for compilation and other tasks.
You can provide them by either declaring a dependency in the ``scala-tool`` configuration or by explicitly defining ``scalaInstance``.

In the first case, add the ``scala-tool`` configuration and add a dependency on ``scala-compiler`` in this configuration.
The organization is not important, but sbt needs the module name to be ``scala-compiler`` and ``scala-library`` in order to handle those jars appropriately.
For example,

::

    managedScalaInstance := false

    // Add the configuration for the dependencies on Scala tool jars
    // You can also use a manually constructed configuration like:
    //   config("scala-tool").hide
    ivyConfigurations += Configurations.ScalaTool

    // Add the usual dependency on the library as well on the compiler in the
    //  'scala-tool' configuration
    libraryDependencies ++= Seq(
       "org.scala-lang" % "scala-library" % scalaVersion.value,
       "org.scala-lang" % "scala-compiler" % scalaVersion.value % "scala-tool"
    )

In the second case, directly construct a value of type `ScalaInstance <../../api/sbt/ScalaInstance.html>`_, typically using a method in the `companion object <../../api/sbt/ScalaInstance$.html>`_, and assign it to ``scalaInstance``.
You will also need to add the ``scala-library`` jar to the classpath to compile and run Scala sources.
For example,

::

    managedScalaInstance := false

    scalaInstance := ...

    unmanagedJars in Compile += scalaInstance.value.libraryJar

Switching to a local Scala version
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To use a locally built Scala version, configure Scala home as described in the following section.
Scala will still be resolved as before, but the jars will come from the configured Scala home directory.


Using Scala from a local directory
==================================

The result of building Scala from source is a Scala home directory ``<base>/build/pack/`` that contains a subdirectory ``lib/`` containing the Scala library, compiler, and other jars.
The same directory layout is obtained by downloading and extracting a Scala distribution.
Such a Scala home directory may be used as the source for jars by setting ``scalaHome``.
For example,

::

    scalaHome := Some(file("/home/user/scala-2.10/"))

By default, ``lib/scala-library.jar`` will be added to the unmanaged classpath and ``lib/scala-compiler.jar`` will be used to compile Scala sources and provide a Scala REPL.
No managed dependency is recorded on ``scala-library``.
This means that Scala will only be resolved from a repository if you explicitly define a dependency on Scala or if Scala is depended on indirectly via a dependency.
In these cases, the artifacts for the resolved dependencies will be substituted with jars in the Scala home ``lib/`` directory.

Mixing with managed dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

As an example, consider adding a dependency on ``scala-reflect`` when ``scalaHome`` is configured:

::

    scalaHome := Some(file("/home/user/scala-2.10/"))

    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

This will be resolved as normal, except that sbt will see if ``/home/user/scala-2.10/lib/scala-reflect.jar`` exists.
If it does, that file will be used in place of the artifact from the managed dependency.

Using unmanaged dependencies only
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Instead of adding managed dependencies on Scala jars, you can directly add them.
The ``scalaInstance`` task provides structured access to the Scala distribution.
For example, to add all jars in the Scala home ``lib/`` directory,

::

    scalaHome := Some(file("/home/user/scala-2.10/"))

    unmanagedJars in Compile ++= scalaInstance.value.jars

To add only some jars, filter the jars from ``scalaInstance`` before adding them.

sbt's Scala version
===================

sbt needs Scala jars to run itself since it is written in Scala.
sbt uses that same version of Scala to compile the build definitions that you write for your project because they use sbt APIs.
This version of Scala is fixed for a specific sbt release and cannot be changed.
For sbt |version|, this version is Scala |scalaVersion|.
Because this Scala version is needed before sbt runs, the repositories used to retrieve this version are configured in the sbt :doc:`launcher </Detailed-Topics/Launcher>`.

