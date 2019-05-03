import java.nio.file.Path

import sjsonnew.BasicJsonProtocol._

val copyFile = taskKey[Int]("dummy task")
copyFile / fileInputs += baseDirectory.value.toGlob / "base" / "*.txt"
copyFile / fileOutputs += baseDirectory.value.toGlob / "out" / "*.txt"
copyFile / target := baseDirectory.value / "out"

copyFile := Def.task {
  val prev = copyFile.previous
  val changes: Option[Seq[Path]] = (copyFile / changedInputFiles).value.map {
    case ChangedFiles(c, _, u) => c ++ u
  }
  prev match {
    case Some(v: Int) if changes.isEmpty => v
    case _ =>
      changes.getOrElse((copyFile / allInputFiles).value).foreach { p =>
        val outDir = baseDirectory.value / "out"
        IO.createDirectory(outDir)
        IO.copyFile(p.toFile, outDir / p.getFileName.toString)
      }
      prev.map(_ + 1).getOrElse(1)
  }
}.value

val checkOutDirectoryIsEmpty = taskKey[Unit]("validates that the output directory is empty")
checkOutDirectoryIsEmpty := {
  assert(fileTreeView.value.list(baseDirectory.value.toGlob / "out" / **).isEmpty)
}

val checkOutDirectoryHasFile = taskKey[Unit]("validates that the output directory is empty")
checkOutDirectoryHasFile := {
  val result = fileTreeView.value.list(baseDirectory.value.toGlob / "out" / **).map(_._1.toFile)
  assert(result == Seq(baseDirectory.value / "out" / "Foo.txt"))
}

val checkCount = inputKey[Unit]("Check that the expected number of evaluations have run.")
checkCount := Def.inputTask {
  val expected = Def.spaceDelimited("").parsed.head.toInt
  val previous = copyFile.previous.getOrElse(0)
  assert(previous == expected)
}.evaluated
