import sbt.nio.Keys._

import scala.util.Try

val fileInputTask = taskKey[Unit]("task with file inputs")

fileInputTask / fileInputs += (baseDirectory.value / "base").toGlob / "*.md"

fileInputTask / inputFileStamper := sbt.nio.FileStamper.LastModified

fileInputTask := {
  /*
   * Normally we'd use an input task for this kind of thing, but input tasks don't work with
   * incremental task evaluation so, instead, we manually set the input in a file. As a result,
   * most of the test commands have to be split into two: one to set the expected result and one
   * to validate it.
   */
  val expectedChanges =
    Try(IO.read(baseDirectory.value / "expected").split(" ").toSeq.filterNot(_.isEmpty))
      .getOrElse(Nil)
      .map(baseDirectory.value.toPath / "base" / _)
  val actual = fileInputTask.changedInputFiles.toSeq.flatMap(_.updated)
  assert(actual.toSet == expectedChanges.toSet)
}

val setExpected = inputKey[Unit]("Writes a space separated list of files")
setExpected := {
  IO.write(baseDirectory.value / "expected", Def.spaceDelimited().parsed.mkString(" "))
}

val setLastModified = taskKey[Unit]("Reset the last modified time")
setLastModified := {
  val file = baseDirectory.value / "base" / "Bar.md"
  IO.setModifiedTimeOrFalse(file, 1234567890L)
}
