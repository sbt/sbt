=======================
Compiler Plugin Support
=======================

There is some special support for using compiler plugins. You can set
``autoCompilerPlugins`` to ``true`` to enable this functionality.

::

    autoCompilerPlugins := true

To use a compiler plugin, you either put it in your unmanaged library
directory (``lib/`` by default) or add it as managed dependency in the
``plugin`` configuration. ``addCompilerPlugin`` is a convenience method
for specifying ``plugin`` as the configuration for a dependency:

::

    addCompilerPlugin("org.scala-tools.sxr" %% "sxr" % "0.2.7")

The ``compile`` and ``testCompile`` actions will use any compiler
plugins found in the ``lib`` directory or in the ``plugin``
configuration. You are responsible for configuring the plugins as
necessary. For example, Scala X-Ray requires the extra option:

::

        // declare the main Scala source directory as the base directory
    scalacOptions :=
        scalacOptions.value :+ ("-Psxr:base-directory:" + (scalaSource in Compile).value.getAbsolutePath)

You can still specify compiler plugins manually. For example:

::

    scalacOptions += "-Xplugin:<path-to-sxr>/sxr-0.2.7.jar"

Continuations Plugin Example
============================

Support for continuations in Scala 2.8 is implemented as a compiler
plugin. You can use the compiler plugin support for this, as shown here.

::

    autoCompilerPlugins := true

    addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.8.1")

    scalacOptions += "-P:continuations:enable"

Version-specific Compiler Plugin Example
========================================

Adding a version-specific compiler plugin can be done as follows:

::

    autoCompilerPlugins := true

    libraryDependencies +=
        compilerPlugin("org.scala-lang.plugins" % "continuations" % scalaVersion.value)

    scalacOptions += "-P:continuations:enable"
