==========
Classpaths
==========

.. howto::
   :id: classpath-types
   :title: Include a new type of managed artifact on the classpath, such as `mar`
   :type: setting

   classpathTypes += "mar"

The :key:`classpathTypes` setting controls the types of managed artifacts that are included on the classpath by default.
To add a new type, such as `mar`, ::

    classpathTypes += "mar"

See the default types included by running `show classpathTypes` at the sbt prompt.


.. howto::
   :id: get-compile-classpath
   :title: Get the classpath used for compilation
   :type: setting

   ... (dependencyClasspath in Compile).value.files ...

The :key:`dependencyClasspath` task scoped to `Compile` provides the classpath to use for compilation.
Its type is `Seq[Attributed[File]]`, which means that each entry carries additional metadata.
The `files` method provides just the raw `Seq[File]` for the classpath.
For example, to use the files for the compilation classpath in another task, ::

    example := {
       val cp: Seq[File] = (dependencyClasspath in Compile).value.files
       ...
    }

.. note::

    This classpath does not include the class directory, which may be necessary for compilation in some situations.


.. howto::
   :id: get-runtime-classpath
   :title: Get the runtime classpath, including the project's compiled classes
   :type: setting

   ... (fullClasspath in Runtime).value.files ...

The :key:`fullClasspath` task provides a classpath including both the dependencies and the products of project.
For the runtime classpath, this means the main resources and compiled classes for the project as well as all runtime dependencies.

The type of a classpath is `Seq[Attributed[File]]`, which means that each entry carries additional metadata.
The `files` method provides just the raw `Seq[File]` for the classpath.
For example, to use the files for the runtime classpath in another task, ::

    example := {
       val cp: Seq[File] = (fullClasspath in Runtime).value.files
       ...
    }

.. howto::
   :id: get-test-classpath
   :title: Get the test classpath, including the project's compiled test classes
   :type: setting

   ... (fullClasspath in Test).value.files ...

The :key:`fullClasspath` task provides a classpath including both the dependencies and the products of a project.
For the test classpath, this includes the main and test resources and compiled classes for the project as well as all dependencies for testing.

The type of a classpath is `Seq[Attributed[File]]`, which means that each entry carries additional metadata.
The `files` method provides just the raw `Seq[File]` for the classpath.
For example, to use the files for the test classpath in another task, ::

    example := {
       val cp: Seq[File] = (fullClasspath in Test).value.files
       ...
    }


.. howto::
   :id: export-jars
   :title: Use packaged jars on classpaths instead of class directories
   :type: setting

   exportJars := true

By default, :key:`fullClasspath` includes a directory containing class files and resources for a project.
This in turn means that tasks like :key:`compile`, :key:`test`, and :key:`run` have these class directories on their classpath.
To use the packaged artifact (such as a jar) instead, configure :key:`exportJars` ::

    exportJars := true

This will use the result of :key:`packageBin` on the classpath instead of the class directory.

.. note::

    Specifically, :key:`fullClasspath` is the concatentation of :key:`dependencyClasspath` and :key:`exportedProducts`.  When :key:`exportJars` is true, :key:`exportedProducts` is the output of `packageBin`.  When :key:`exportJars` is false, :key:`exportedProducts` is just :key:`products`, which is by default the directory containing class files and resources.


.. howto::
   :id: managed-jars-in-config
   :title: Get all managed jars for a configuration
   :type: setting

   ... Classpaths.managedJars(config, artifactTypes, update.value) ...

The result of the :key:`update` task has type :doc:`UpdateReport </Detailed-Topics/Update-Report>`, which contains the results of dependency resolution.
This can be used to extract the files for specific types of artifacts in a specific configuration.
For example, to get the jars and zips of dependencies in the `Compile` configuration, ::

    example := {
       val artifactTypes = Set("jar", "zip")
       val files: Seq[File] =
          Classpaths.managedJars(Compile, artifactTypes, update.value)
       ...
    }


.. howto::
   :id: classpath-files
   :title: Get the files included in a classpath
   :type: setting

   ... someClasspath.files ...

A classpath has type `Seq[Attributed[File]]`, which means that each entry carries additional metadata.
The `files` method provides just the raw `Seq[File]` for the classpath.
For example, ::

    val cp: Seq[Attributed[File]] = ...
    val files: Seq[File] = cp.files


.. howto::
   :id: classpath-entry-module
   :title: Get the module and artifact that produced a classpath entry
   :type: text

A classpath has type `Seq[Attributed[File]]`, which means that each entry carries additional metadata.
This metadata is in the form of an `AttributeMap <../../api/sbt/AttributeMap.html>`_.
Useful keys for entries in the map are `artifact.key`, `module.key`, and :key:`analysis`.
For example, ::

    val classpath: Seq[Attributed[File]] = ???
    for(entry <- classpath) yield {
       val art: Option[Artifact] = entry.get(artifact.key)
       val mod: Option[ModuleID] = entry.get(module.key)
       val an: Option[inc.Analysis] = entry.get(analysis)
       ...
    }

.. note::

    Entries may not have some or all metadata.
    Only entries from source dependencies, such as internal projects, have an incremental compilation `Analysis <../../api/sbt/inc/Analysis.html>`_.
    Only entries for managed dependencies have an `Artifact <../../api/sbt/Artifact.html>`_ and `ModuleID <../../api/sbt/ModuleID.html>`_.
