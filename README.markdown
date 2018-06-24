sbt-projectmatrix
=================

cross building using subprojects.

This is an experimental plugin that implements better cross building.

setup
-----

**Requirements**: Requires sbt 1.2.0-M1 or above.

In `project/plugins.sbt`:

```scala
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.1.0")
```

usage
-----

To use `projectMatrix`:

```scala
lazy val core = (projectMatrix in file("core"))
  .scalaVersions("2.12.6", "2.11.12")
  .settings(
    name := "core"
  )
  .jvmPlatform()

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .scalaVersions("2.12.6")
  .settings(
    name := "app"
  )
  .jvmPlatform()
```

This sets up basic project matrices one supporting both 2.11 and 2.12, and the other supporting only 2.12.

license
-------

MIT License
