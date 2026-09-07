Adds `crossProjectSources`, so sbt can read the source directories using layout compatible with
`sbt-crossproject` plugin.

```scala
lazy val core = (projectMatrix in file("core"))
  .crossProjectSources
  .jvmPlatform(scalaVersions = Seq(scala213, scala3))
  .jsPlatform(scalaVersions = Seq(scala3))
```

This `.crossProjectSources` is a shortcut for `crossProjectSources := true` in the project settings.
Thus, `ThisBuild / crossProjectSources := true` sets it for every project matrix in the build.

Normally, sbt reads the following directories for a JVM row of a project matrix
(every axis is part of the leaf name, under one `src`):

```
src/main/scala          src/main/java          src/main/resources
src/main/scalajvm       src/main/javajvm
src/main/scalajvm-3
```

With `crossProjectSources`, sbt will also read a second layout which puts
all platform groupings above `src`, with each group of platforms with a
directory of its own; again, using JVM as an example:

```
shared/src/main/scala   shared/src/main/scala-3   shared/src/main/resources
jvm/src/main/scala      jvm/src/main/java         jvm/src/main/resources
js-jvm/src/main/scala   jvm-native/src/main/scala
```

`shared` is read for every platform, while `java` is only under the row's own platform: a Scala.js
row, for example, reads `js/src/main/java`.

Every Scala tree above comes in one form per prefix of the row's Scala version, longest first, and
then `scala`. `jvmPlatform` names the binary version, so a Scala 3 row has two forms and a 2.13 row
three, under every tree of its own layout, `shared` for example:

```
shared/src/main/scala-2.13   shared/src/main/scala-2   shared/src/main/scala
```

A row built with `VirtualAxis.scalaPartialVersion` or with `CrossVersion.full` names a longer
version, and gets a tree for each of its prefixes: a `2.13.18` row reads `scala-2.13.18`,
`scala-2.13`, `scala-2` and `scala`.

The default layout has no epoch form: a 2.13 JVM row reads `src/main/scalajvm-2.13` and
`src/main/scalajvm`, and no `src/main/scalajvm-2`. Its epoch tree is sbt's own `src/main/scala-2`,
which every 2.13 row reads whatever its platform.
