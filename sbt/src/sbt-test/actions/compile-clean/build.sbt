import sbt.nio.file.syntax._

cleanKeepGlobs in Compile +=
  ((classDirectory in Compile in compile).value / "X.class").toGlob
