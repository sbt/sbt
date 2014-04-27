================
Generating files
================

sbt provides standard hooks for adding source and resource generation tasks.

.. howto::
   :id: sources
   :title: Generate sources
   :type: setting

   sourceGenerators in Compile += <task of type Seq[File]>.taskValue

A source generation task should generate sources in a subdirectory of :key:`sourceManaged` and return a sequence of files generated. The signature of a source generation function (that becomes a basis for a task) is usually as follows:

::

    def makeSomeSources(base: File): Seq[File]

The key to add the task to is called :key:`sourceGenerators`.  Because we want to add the task, and not the value after its execution, we use `taskValue` instead of the usual `value`.  :key:`sourceGenerators` should be scoped according to whether the generated files are main (`Compile`) or test (`Test`) sources.  This basic structure looks like:

::

    sourceGenerators in Compile += <task of type Seq[File]>.taskValue

For example, assuming a method `def makeSomeSources(base: File): Seq[File]`,

::

    sourceGenerators in Compile += Def.task {
      makeSomeSources((sourceManaged in Compile).value / "demo")
    }.taskValue

As a specific example, the following source generator generates `Test.scala` application object that once executed, prints `"Hi"` to the console:

::

    sourceGenerators in Compile += Def.task {
      val file = (sourceManaged in Compile).value / "demo" / "Test.scala"
      IO.write(file, """object Test extends App { println("Hi") }""")
      Seq(file)
    }.taskValue

Executing `run` will print `"Hi"`.

::

    > run
    [info] Running Test
    Hi

Change `Compile` to `Test` to make it a test source.  For efficiency, you would only want to generate sources when necessary and not every run.

By default, generated sources are not included in the packaged source artifact.  To do so, add them as you would other mappings.  See :ref:`Adding files to a package <modify-package-contents>`.  A source generator can return both Java and Scala sources mixed together in the same sequence.  They will be distinguished by their extension later.

.. howto::
   :id: resources
   :title: Generate resources
   :type: setting

   resourceGenerators in Compile += <task of type Seq[File]>.taskValue

A resource generation task should generate resources in a subdirectory of :key:`resourceManaged` and return a sequence of files generated. Like a source generation function, the signature of a resource generation function (that becomes a basis for a task) is usually as follows:

::

    def makeSomeResources(base: File): Seq[File]

The key to add the task to is called :key:`resourceGenerators`.  Because we want to add the task, and not the value after its execution, we use `taskValue` instead of the usual `value`.  It should be scoped according to whether the generated files are main (`Compile`) or test (`Test`) resources.  This basic structure looks like:

::

    resourceGenerators in Compile += <task of type Seq[File]>.taskValue

For example, assuming a method `def makeSomeResources(base: File): Seq[File]`,

::

    resourceGenerators in Compile += Def.task {
      makeSomeResources((resourceManaged in Compile).value / "demo")
    }.taskValue

As a specific example, the following generates a properties file `myapp.properties` containing the application name and version:

::

    resourceGenerators in Compile += Def.task {
      val file = (resourceManaged in Compile).value / "demo" / "myapp.properties"
      val contents = "name=%s\nversion=%s".format(name.value,version.value)
      IO.write(file, contents)
      Seq(file)
    }.taskValue

Change `Compile` to `Test` to make it a test resource.  Normally, you would only want to generate resources when necessary and not every run.

By default, generated resources are not included in the packaged source artifact.  To do so, add them as you would other mappings.  See :ref:`Adding files to a package <modify-package-contents>`.
