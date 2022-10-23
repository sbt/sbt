import sbt.nio.file.Glob

Compile / cleanKeepGlobs +=
  Glob((Compile / compile / classDirectory).value, "X.class")
