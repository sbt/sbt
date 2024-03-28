import sbt.nio.file.Glob

name := "compile-clean"
scalaVersion := "2.12.17"
Compile / cleanKeepGlobs +=
  Glob((Compile / compile / classDirectory).value, "X.class")
