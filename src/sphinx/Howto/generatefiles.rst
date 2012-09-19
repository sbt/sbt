================
Generating files
================

sbt provides standard hooks for adding source or resource generation tasks.

.. howto::
   :id: sources
   :title: Generate sources
   :type: setting

   sourceGenerators in Compile <+= <your Task[Seq[File]] here>

A source generation task should generate sources in a subdirectory of ``sourceManaged`` and return a sequence of files generated.  The key to add the task to is called ``sourceGenerators``.  It should be scoped according to whether the generated files are main (``Compile``) or test (``Test``) sources.  This basic structure looks like:

::

    sourceGenerators in Compile <+= <your Task[Seq[File]] here>

For example, assuming a method ``def makeSomeSources(base: File): Seq[File]``,

::

    sourceGenerators in Compile <+= sourceManaged in Compile map { outDir: File =>
      makeSomeSources(outDir / "demo")
    }

As a specific example, the following generates a hello world source file:

::

    sourceGenerators in Compile <+= sourceManaged in Compile map { dir =>
      val file = dir / "demo" / "Test.scala"
      IO.write(file, """object Test extends App { println("Hi") }""")
      Seq(file)
    }

Executing 'run' will print "Hi".  Change ``Compile`` to ``Test`` to make it a test source.  For efficiency, you would only want to generate sources when necessary and not every run.

By default, generated sources are not included in the packaged source artifact.  To do so, add them as you would other mappings.  See ``Adding files to a package``.

.. howto::
   :id: resources
   :title: Generate resources
   :type: setting

   resourceGenerators in Compile <+= <your Task[Seq[File]] here>

A resource generation task should generate resources in a subdirectory of ``resourceManaged`` and return a sequence of files generated.  The key to add the task to is called ``resourceGenerators``.  It should be scoped according to whether the generated files are main (``Compile``) or test (``Test``) resources.  This basic structure looks like:

::

    resourceGenerators in Compile <+= <your Task[Seq[File]] here>

For example, assuming a method ``def makeSomeResources(base: File): Seq[File]``,

::

    resourceGenerators in Compile <+= resourceManaged in Compile map { outDir: File =>
      makeSomeResources(outDir / "demo")
    }

As a specific example, the following generates a properties file containing the application name and version:

::

    resourceGenerators in Compile <+= 
      (resourceManaged in Compile, name, version) map { (dir, n, v) =>
        val file = dir / "demo" / "myapp.properties"
        val contents = "name=%s\nversion=%s".format(n,v)
        IO.write(file, contents)
        Seq(file)
      }
    }

Change ``Compile`` to ``Test`` to make it a test resource.  Normally, you would only want to generate resources when necessary and not every run.

By default, generated resources are not included in the packaged source artifact.  To do so, add them as you would other mappings.  See the ``Adding files to a package`` section.
