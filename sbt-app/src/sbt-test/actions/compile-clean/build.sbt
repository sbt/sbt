import sbt.nio.file.Glob

Global / localCacheDirectory := (ThisBuild / baseDirectory).value / "target" / "bootcache"
name := "compile-clean"
scalaVersion := "2.12.21"
Compile / cleanKeepGlobs +=
  Glob(target.value) / RecursiveGlob  / "X.class"
