import java.nio.file.Path

import sjsonnew.BasicJsonProtocol._

val copyFile = taskKey[Int]("dummy task")
copyFile / fileInputs += baseDirectory.value.toGlob / "base" / "*.txt"
copyFile / fileOutputs += baseDirectory.value.toGlob / "out" / "*.txt"
copyFile / target := baseDirectory.value / "out"

copyFile := Def.task {
  val prev = copyFile.previous
  val changes: Option[Seq[Path]] = copyFile.inputFileChanges match {
    case fc @ FileChanges(c, _, u, _) if fc.hasChanges => Some(c ++ u)
    case _ => None
  }
  prev match {
    case Some(v: Int) if changes.isEmpty => v
    case _ =>
      changes.getOrElse(copyFile.inputFiles).foreach { p =>
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

commands += Command.single("checkCount") { (s, digits) =>
  s"writeCount $digits" :: "checkCountImpl" :: s
}

val writeCount = inputKey[Unit]("writes the count to a file")
writeCount := IO.write(baseDirectory.value / "expectedCount", Def.spaceDelimited().parsed.head)
val checkCountImpl = taskKey[Unit]("Check that the expected number of evaluations have run.")
checkCountImpl := {
  val expected = IO.read(baseDirectory.value / "expectedCount").toInt
  val previous = copyFile.previous.getOrElse(0)
  assert(previous == expected)
}
