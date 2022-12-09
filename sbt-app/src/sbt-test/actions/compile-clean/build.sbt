import sbt.nio.file.Glob

ThisBuild / scalaVersion := "2.12.17"
Compile / cleanKeepGlobs +=
  Glob((Compile / compile / classDirectory).value, "X.class")
