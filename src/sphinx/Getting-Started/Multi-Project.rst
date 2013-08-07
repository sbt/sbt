====================
Multi-Project Builds
====================

This page introduces multiple projects in a single build.

Please read the earlier pages in the Getting Started Guide first, in
particular you need to understand :doc:`build.sbt <Basic-Def>` before
reading this page.

Multiple projects
-----------------

It can be useful to keep multiple related projects in a single build,
especially if they depend on one another and you tend to modify them
together.

Each sub-project in a build has its own source directories, generates
its own jar file when you run `package`, and in general works like any
other project.

A project is defined by declaring a `lazy val` of type `Project <../../api/sbt/Project.html>`_.
For example, ::

    lazy val util = project

    lazy val core = project

The name of the val is used as the project's ID and base directory name.
The ID is used to refer to the project at the command line.
The base directory may be changed from the default using the `in` method.
For example, the following is a more explicit way to write the previous example: ::

    lazy val util = project.in( file("util") )

    lazy val core = project in file("core")

Dependencies
------------

Projects in the build can be completely independent of one another, but usually
they will be related to one another by some kind of dependency.  There are two
types of dependencies: aggregate and classpath.


Aggregation
~~~~~~~~~~~

Aggregation means that running a task on the aggregate project will also
run it on the aggregated projects. For example, ::

    lazy val root =
	    project.in( file(".") )
       .aggregate(util, core)

    lazy val util = project

    lazy val core = project


In the above example, the root project aggregates `util` and `core`.
Start up sbt with two subprojects as in the example, and try `compile`. 
You should see that all three projects are compiled.

*In the project doing the aggregating*, the `root` project in
this case, you can control aggregation per-task.
For example, to avoid aggregating the `update` task:

::

    lazy val root =
	    project.in( file(".") )
       .aggregate(util, core)
       .settings(
         aggregate in update := false
       )

    [...]

`aggregate in update` is the `aggregate` key scoped to the `update` task.
(See :doc:`scopes <Scopes>`.)

Note: aggregation will run the aggregated tasks in parallel and with no
defined ordering between them.

Classpath dependencies
~~~~~~~~~~~~~~~~~~~~~~

A project may depend on code in another project. This is done by adding
a `dependsOn` method call. For example, if `core` needed
`util` on its classpath, you would define `core` as:

::

    lazy val core = project.dependsOn(util)

Now code in `core` can use classes from `util`.
This also creates an ordering between the projects when compiling them;
`util` must be updated and compiled before `core` can be compiled.

To depend on multiple projects, use multiple arguments to `dependsOn`,
like `dependsOn(bar, baz)`.

Per-configuration classpath dependencies
++++++++++++++++++++++++++++++++++++++++

`foo dependsOn(bar)` means that the `compile` configuration in
`foo` depends on the `compile` configuration in `bar`. You could
write this explicitly as `dependsOn(bar % "compile->compile")`.

The `->` in `"compile->compile"` means "depends on" so
`"test->compile"` means the `test` configuration in `foo` would
depend on the `compile` configuration in `bar`.

Omitting the `->config` part implies `->compile`, so
`dependsOn(bar % "test")` means that the `test` configuration in
`foo` depends on the `Compile` configuration in `bar`.

A useful declaration is `"test->test"` which means `test` depends on `test`.
This allows you to put utility code for testing in `bar/src/test/scala` and then use that code in `foo/src/test/scala`,
for example.

You can have multiple configurations for a dependency, separated by
semicolons. For example,
`dependsOn(bar % "test->test;compile->compile")`.


Default root project
--------------------

If a project is not defined for the root directory in the build, sbt creates a default
one that aggregates all other projects in the build.


Because project `hello-foo` is defined with `base = file("foo")`, it
will be contained in the subdirectory `foo`. Its sources could be
directly under `foo`, like `foo/Foo.scala`, or in
`foo/src/main/scala`. The usual sbt :doc:`directory structure <Directories>`
applies underneath `foo` with the exception of build definition files.

Any `.sbt` files in `foo`, say `foo/build.sbt`, will be merged
with the build definition for the entire build, but scoped to the
`hello-foo` project.

If your whole project is in `hello`, try defining a different version
(`version := "0.6"`) in `hello/build.sbt`, `hello/foo/build.sbt`,
and `hello/bar/build.sbt`. Now `show version` at the sbt interactive
prompt. You should get something like this (with whatever versions you
defined):

.. code-block:: console

    > show version
    [info] hello-foo/*:version
    [info]  0.7
    [info] hello-bar/*:version
    [info]  0.9
    [info] hello/*:version
    [info]  0.5

`hello-foo/*:version` was defined in `hello/foo/build.sbt`,
`hello-bar/*:version` was defined in `hello/bar/build.sbt`, and
`hello/*:version` was defined in `hello/build.sbt`. Remember the
:doc:`syntax for scoped keys <Scopes>`. Each `version` key
is scoped to a project, based on the location of the `build.sbt`. But
all three `build.sbt` are part of the same build definition.

*Each project's settings can go in `.sbt` files in the base directory
of that project*, while the `.scala` file can be as simple as the one
shown above, listing the projects and base directories. *There is no
need to put settings in the `.scala` file.*

You may find it cleaner to put everything including settings in
`.scala` files in order to keep all build definition under a single
`project` directory, however. It's up to you.

You cannot have a `project` subdirectory or `project/*.scala` files
in the sub-projects. `foo/project/Build.scala` would be ignored.

Navigating projects interactively
---------------------------------

At the sbt interactive prompt, type `projects` to list your projects
and `project <projectname>` to select a current project. When you run
a task like `compile`, it runs on the current project. So you don't
necessarily have to compile the root project, you could compile only a
subproject.

You can run a task in another project by explicitly specifying the
project ID, such as `subProjectID/compile`.

Common code
-----------

The definitions in `.sbt` files are not visible in other `.sbt` files.
In order to share code between `.sbt` files, define one or more Scala
files in the `project/` directory of the build root.  This directory
is also an sbt project, but for your build.

For example:

`<root>/project/Common.scala` ::

    import sbt._
    import Keys._

    object Common {
      def text = "org.example"
    }

`<root>`/build.sbt ::

    organization := Common.text

See :doc:`Full-Def` for details.

Next
----

Move on to :doc:`using plugins <Using-Plugins>`.
