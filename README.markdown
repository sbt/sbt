sbt-projectmatrix
=================

cross building using subprojects.

This is an experimental plugin that implements better cross building.

setup
-----

**Requirements**: Requires sbt 1.2.0 or above.

In `project/plugins.sbt`:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.3.0")

// add also the following for Scala.js support
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.27")
```

usage
-----

### building against multiple Scala versions

After adding sbt-projectmatrix to your build, here's how you can set up a matrix with two Scala versions.

```scala
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .jvmPlatform(scalaVersions = Seq("2.12.8", "2.11.12"))
```

This will create subprojects `coreJVM2_11` and `coreJVM2_12`.
Unlike `++` style stateful cross building, these will build in parallel.

### two matrices

It gets more interesting if you have more than one matrix.

```scala
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// uncomment if you want root
// lazy val root = (project in file("."))
//   .aggregate(core.projectRefs ++ app.projectRefs: _*)
//   .settings(
//   )

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .jvmPlatform(scalaVersions = Seq("2.12.8", "2.11.12"))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app"
  )
  .jvmPlatform(scalaVersions = Seq("2.12.8"))
```

This is an example where `core` builds against Scala 2.11 and 2.12, but app only builds for one of them.

### Scala.js support

[Scala.js](http://scala-js.org/) support was added in sbt-projectmatrix 0.2.0.
To use this, you need to setup sbt-scalajs as well:

```scala
lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .jsPlatform(scalaVersions = Seq("2.12.8", "2.11.12"))
```

This will create subprojects `coreJS2_11` and `coreJS2_12`.

### Scala Native support

[Scala Native](http://scala-native.org) support will be added in upcoming release.
To use this, you need to setup sbt-scala-native` as well:

```scala
lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .nativePlatform(scalaVersions = Seq("2.11.12"))
```

This will create subproject `coreNative2_11`.

### parallel cross-library building

The rows can also be used for parallel cross-library building.
For example, if you want to build against Config 1.2 and Config 1.3, you can do something like this:

```scala
ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .crossLibrary(
    scalaVersions = Seq("2.12.8", "2.11.12"),
    suffix = "Config1.2",
    settings = Seq(
      libraryDependencies += "com.typesafe" % "config" % "1.2.1"
    )
  )
  .crossLibrary(
    scalaVersions = Seq("2.12.8"),
    suffix = "Config1.3",
    settings = Seq(
      libraryDependencies += "com.typesafe" % "config" % "1.3.3"
    )
  )
```

This will create `coreConfig1_22_11`, `coreConfig1_22_12`, and `coreConfig1_32_12` respectively producing `core_config1.3_2.12`, `core_config1.2_2.11`, and `core_config1.2_2.12` artifacts.

### referncing the generated subprojects

You might want to reference to one of the projects within `build.sbt`.

```scala
lazy val core12 = core.jvm("2.12.8")

// lazy val core12 = core.crossLib("Config1.2")("2.12.8")
```

In the above `core12` returns `Project` type.

credits
-------

- The idea of representing cross build using subproject was pionieered by Tobias Schlatter's work on Scala.js plugin, which was later expanded to [ sbt-crossproject](https://github.com/portable-scala/sbt-crossproject). However, this only addresses the platform (JVM, JS, Native) cross building.
- [sbt-cross](https://github.com/lucidsoftware/sbt-cross) written by Paul Draper in 2015 implements cross building across Scala versions.

license
-------

MIT License
