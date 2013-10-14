=================
Customizing paths
=================

This page describes how to modify the default source, resource, and library directories and what files get included from them.

.. howto::
   :id: scala-source-directory
   :title: Change the default Scala source directory
   :type: setting

   scalaSource in Compile := baseDirectory.value / "src"

The directory that contains the main Scala sources is by default `src/main/scala`.
For test Scala sources, it is `src/test/scala`.
To change this, modify :key:`scalaSource` in the `Compile` (for main sources) or `Test` (for test sources).
For example, ::

   scalaSource in Compile := baseDirectory.value / "src"

   scalaSource in Test := baseDirectory.value / "test-src"

.. note::

    The Scala source directory can be the same as the Java source directory.

.. howto::
   :id: java-source-directory
   :title: Change the default Java source directory
   :type: setting

   javaSource in Compile := baseDirectory.value / "src"

The directory that contains the main Java sources is by default `src/main/java`.
For test Java sources, it is `src/test/java`.
To change this, modify :key:`javaSource` in the `Compile` (for main sources) or `Test` (for test sources).

For example, ::

   javaSource in Compile := baseDirectory.value / "src"

   javaSource in Test := baseDirectory.value / "test-src"

.. note::

    The Scala source directory can be the same as the Java source directory.

.. howto::
   :id: resource-directory
   :title: Change the default resource directory
   :type: setting

   resourceDirectory in Compile := baseDirectory.value / "resources"

The directory that contains the main resources is by default `src/main/resources`.
For test resources, it is `src/test/resources`.
To change this, modify :key:`resourceDirectory` in either the `Compile` or `Test` configuration.

For example, ::

   resourceDirectory in Compile := baseDirectory.value / "resources"

   resourceDirectory in Test := baseDirectory.value / "test-resources"


.. howto::
   :id: unmanaged-base-directory
   :title: Change the default (unmanaged) library directory
   :type: setting

   unmanagedBase := baseDirectory.value / "jars"

The directory that contains the unmanaged libraries is by default `lib/`.
To change this, modify :key:`unmanagedBase`.
This setting can be changed at the project level or in the `Compile`, `Runtime`, or `Test` configurations.


When defined without a configuration, the directory is the default directory for all configurations.
For example, the following declares `jars/` as containing libraries: ::

   unmanagedBase := baseDirectory.value / "jars"

When set for `Compile`, `Runtime`, or `Test`, :key:`unmanagedBase` is the directory containing libraries for that configuration, overriding the default.
For example, the following declares `lib/main/` to contain jars only for `Compile` and not for running or testing: ::

   unmanagedBase in Compile := baseDirectory.value / "lib" / "main"



.. howto::
   :id: disable-base-sources
   :title: Disable using the project's base directory as a source directory
   :type: setting

    sourcesInBase := false

By default, sbt includes `.scala` files from the project's base directory as main source files.
To disable this, configure :key:`sourcesInBase`: ::

    sourcesInBase := false


.. howto::
   :id: add-source-directory
   :title: Add an additional source directory
   :type: setting

    unmanagedSourceDirectories in Compile += baseDirectory.value / "extra-src"

sbt collects :key:`sources` from :key:`unmanagedSourceDirectories`, which by default consists of :key:`scalaSource` and :key:`javaSource`.
Add a directory to :key:`unmanagedSourceDirectories` in the appropriate configuration to add a source directory.
For example, to add `extra-src` to be an additional directory containing main sources, ::

    unmanagedSourceDirectories in Compile += baseDirectory.value / "extra-src"

.. note::

    This directory should only contain unmanaged sources, which are sources that are manually created and managed.
    See :doc:`/Howto/generatefiles` for working with automatically generated sources.


.. howto::
   :id: add-resource-directory
   :title: Add an additional resource directory
   :type: setting

    unmanagedResourceDirectories in Compile += baseDirectory.value / "extra-resources"

sbt collects :key:`resources` from :key:`unmanagedResourceDirectories`, which by default consists of :key:`resourceDirectory`.
Add a directory to :key:`unmanagedResourceDirectories` in the appropriate configuration to add another resource directory.
For example, to add `extra-resources` to be an additional directory containing main resources, ::

    unmanagedResourceDirectories in Compile += baseDirectory.value / "extra-resources"

.. note::

    This directory should only contain unmanaged resources, which are resources that are manually created and managed.
    See :doc:`/Howto/generatefiles` for working with automatically generated resources.


.. howto::
   :id: source-include-filter
   :title: Include/exclude files in the source directory
   :type: setting

    includeFilter in unmanagedSources := "*.scala" || "*.java"

When sbt traverses :key:`unmanagedSourceDirectories` for sources, it only includes directories and files that match :key:`includeFilter` and do not match :key:`excludeFilter`.
:key:`includeFilter` and :key:`excludeFilter` have type `java.io.FileFilter` and sbt :ref:`provides some useful combinators <file-filter>` for constructing a `FileFilter`.
For example, in addition to the default hidden files exclusion, the following also ignores files containing `impl` in their name, ::

    excludeFilter in unmanagedSources := HiddenFileFilter || "*impl*"

To have different filters for main and test libraries, configure `Compile` and `Test` separately: ::

    includeFilter in (Compile, unmanagedSources) := "*.scala" || "*.java"

    includeFilter in (Test, unmanagedSources) := HiddenFileFilter || "*impl*"

.. note::

    By default, sbt includes `.scala` and `.java` sources, excluding hidden files.


.. howto::
   :id: resource-include-filter
   :title: Include/exclude files in the resource directory
   :type: setting

    includeFilter in unmanagedResources := "*.txt" || "*.html"

When sbt traverses :key:`unmanagedResourceDirectories` for resources, it only includes directories and files that match :key:`includeFilter` and do not match :key:`excludeFilter`.
:key:`includeFilter` and :key:`excludeFilter` have type `java.io.FileFilter` and sbt :ref:`provides some useful combinators <file-filter>` for constructing a `FileFilter`.
For example, in addition to the default hidden files exclusion, the following also ignores files containing `impl` in their name, ::

    excludeFilter in unmanagedSources := HiddenFileFilter || "*impl*"

To have different filters for main and test libraries, configure `Compile` and `Test` separately: ::

    includeFilter in (Compile, unmanagedSources) := "*.txt"

    includeFilter in (Test, unmanagedSources) := "*.html"

.. note::

    By default, sbt includes all files that are not hidden.





.. howto::
   :id: lib-include-filter
   :title: Include only certain (unmanaged) libraries
   :type: setting

    includeFilter in unmanagedJars := "*.jar" || "*.zip"

When sbt traverses :key:`unmanagedBase` for resources, it only includes directories and files that match :key:`includeFilter` and do not match :key:`excludeFilter`.
:key:`includeFilter` and :key:`excludeFilter` have type `java.io.FileFilter` and sbt :ref:`provides some useful combinators <file-filter>` for constructing a `FileFilter`.
For example, in addition to the default hidden files exclusion, the following also ignores zips, ::

    excludeFilter in unmanagedJars := HiddenFileFilter || "*.zip"

To have different filters for main and test libraries, configure `Compile` and `Test` separately: ::

    includeFilter in (Compile, unmanagedJars) := "*.jar"

    includeFilter in (Test, unmanagedJars) := "*.jar" || "*.zip"

.. note::

    By default, sbt includes jars, zips, and native dynamic libraries, excluding hidden files.
