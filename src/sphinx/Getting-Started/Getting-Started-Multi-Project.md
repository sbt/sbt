# Multi-Project Builds

[[Previous|Getting Started Using Plugins]] _Getting Started Guide page
12 of 14._ [[Next|Getting Started Custom Settings]]

This page introduces multiple projects in a single build.

Please read the earlier pages in the Getting Started Guide first,
in particular you need to understand
[[build.sbt|Getting Started Basic Def]] and
[[.scala build definition|Getting Started Full Def]] before reading
this page.

## Multiple projects

It can be useful to keep multiple related projects in a single build,
especially if they depend on one another and you tend to modify them
together.

Each sub-project in a build has its own `src/main/scala`, generates its own
jar file when you run `package`, and in general works like any other
project.

## Defining projects in a `.scala` file

To have multiple projects, you must declare each project and how they relate
in a `.scala` file; there's no way to do it in a `.sbt` file. However, you
can define settings for each project in `.sbt` files. Here's an example of a
`.scala` file which defines a root project `hello`, where the root project
aggregates two sub-projects, `hello-foo` and `hello-bar`:

```scala
import sbt._
import Keys._

object HelloBuild extends Build {
    lazy val root = Project(id = "hello",
                            base = file(".")) aggregate(foo, bar)

    lazy val foo = Project(id = "hello-foo",
                           base = file("foo"))

    lazy val bar = Project(id = "hello-bar",
                           base = file("bar"))
}
```

sbt finds the list of `Project` objects using reflection, looking for fields
with type `Project` in the `Build` object.

Because project `hello-foo` is defined with `base = file("foo")`, it will be
contained in the subdirectory `foo`. Its sources could be directly under
`foo`, like `foo/Foo.scala`, or in `foo/src/main/scala`. The usual sbt
[[directory structure|Getting Started Directories]] applies underneath `foo` with
the exception of build definition files.

Any `.sbt` files in `foo`, say `foo/build.sbt`, will be merged with the
build definition for the entire build, but scoped to the `hello-foo`
project.

If your whole project is in `hello`, try defining a different version
(`version := "0.6"`) in `hello/build.sbt`, `hello/foo/build.sbt`, and
`hello/bar/build.sbt`.  Now `show version` at the sbt interactive
prompt. You should get something like this (with whatever versions you
defined):

```text
> show version
[info] hello-foo/*:version
[info] 	0.7
[info] hello-bar/*:version
[info] 	0.9
[info] hello/*:version
[info] 	0.5
```

`hello-foo/*:version` was defined in `hello/foo/build.sbt`,
`hello-bar/*:version` was defined in `hello/bar/build.sbt`, and
`hello/*:version` was defined in `hello/build.sbt`. Remember the
[[syntax for scoped keys|Getting Started Scopes]]. Each `version` key is scoped to a
project, based on the location of the `build.sbt`. But all three `build.sbt`
are part of the same build definition.

_Each project's settings can go in `.sbt` files in the base
directory of that project_, while the `.scala` file can be as simple as the
one shown above, listing the projects and base directories. _There is no need
to put settings in the `.scala` file._

You may find it cleaner to put everything including settings in
`.scala` files in order to keep all build definition under a
single `project` directory, however. It's up to you.

You cannot have a `project` subdirectory or `project/*.scala` files in the
sub-projects. `foo/project/Build.scala` would be ignored.

## Aggregation

Projects in the build can be completely independent of one another, if you
want.

In the above example, however, you can see the method call `aggregate(foo, bar)`.
This aggregates `hello-foo` and `hello-bar` underneath the root project.

Aggregation means that running a task on the aggregate project will also run
it on the aggregated projects. Start up sbt with two subprojects as in the
example, and try `compile`. You should see that all three projects are
compiled.

_In the project doing the aggregating_, the root `hello` project in this
case, you can control aggregation per-task. So for example in
`hello/build.sbt` you could avoid aggregating the `update` task:

```scala
aggregate in update := false
```

`aggregate in update` is the `aggregate` key scoped to the `update` task,
see [[scopes|Getting Started Scopes]].

Note: aggregation will run the aggregated tasks in parallel and with no defined
ordering.

## Classpath dependencies

A project may depend on code in another project. This is done by adding a
`dependsOn` method call. For example, if `hello-foo` needed `hello-bar` on its classpath, 
you would write in your `Build.scala`:

```scala
    lazy val foo = Project(id = "hello-foo",
                           base = file("foo")) dependsOn(bar)
```

Now code in `hello-foo` can use classes from `hello-bar`. This also creates
an ordering between the projects when compiling them; `hello-bar` must be
updated and compiled before `hello-foo` can be compiled.

To depend on multiple projects, use multiple arguments to `dependsOn`, like
`dependsOn(bar, baz)`.

### Per-configuration classpath dependencies

`foo dependsOn(bar)` means that the `Compile` configuration in `foo` depends
on the `Compile` configuration in `bar`. You could write this explicitly as
`dependsOn(bar % "compile->compile")`.

The `->` in `"compile->compile"` means "depends on" so `"test->compile"`
means the `Test` configuration in `foo` would depend on the `Compile`
configuration in `bar`.

Omitting the `->config` part implies `->compile`, so `dependsOn(bar %
"test")` means that the `Test` configuration in `foo` depends on the
`Compile` configuration in `bar`.

A useful declaration is `"test->test"` which means `Test` depends on
`Test`. This allows you to put utility code for testing in
`bar/src/test/scala` and then use that code in `foo/src/test/scala`, for
example.

You can have multiple configurations for a dependency, separated by
semicolons. For example, `dependsOn(bar % "test->test;compile->compile")`.

## Navigating projects interactively

At the sbt interactive prompt, type `projects` to list your projects and
`project <projectname>` to select a current project. When you run a task
like `compile`, it runs on the current project. So you don't necessarily
have to compile the root project, you could compile only a subproject.

## Sharing settings

When having a single `.scala` file setting up the different projects, it's easy to use reuse settings across different projects. But even when using different `build.sbt` files, it's still easy to share settings across all projects from the main build, by using the `ThisBuild` scope to make a setting apply globally. For instance, when a main project depends on a subproject, these two projects must typically be compiled with the same Scala version. To set it only once, it is enough to write, in the main `build.sbt` file, the following line:

```scala
scalaVersion in ThisBuild := "2.10.0"
```

replacing `2.10.0` with the desired Scala version. This setting will propagate across all subprojects. For more information on the `ThisBuild` scope, go back to the [[page on scopes|Getting Started Scopes]].

## Next

Move on to create [[custom settings|Getting Started Custom Settings]].