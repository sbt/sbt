import sbt.nio.file.Glob

Global / cacheStores := Seq.empty
name := "compile-clean"
scalaVersion := "2.12.20"
Compile / cleanKeepGlobs +=
  Glob(target.value) / RecursiveGlob  / "X.class"
