import sbt.nio.file.Glob

cleanKeepGlobs in Compile +=
  Glob((classDirectory in Compile in compile).value, "X.class")
