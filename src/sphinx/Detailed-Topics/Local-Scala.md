# Local Scala

To use a locally built Scala version, define the `scala-home` setting, which is of type `Option[File]`.
This Scala version will only be used for the build and not for sbt, which will still use the version it was compiled against.

Example:
```scala
scalaHome := Some(file("/path/to/scala"))
```

Using a local Scala version will override the `scala-version` setting and will not work with [[cross building|Cross Build]].

sbt reuses the class loader for the local Scala version.  If you recompile your local Scala version and you are using sbt interactively, run
```text
> reload
```

to use the new compilation results.