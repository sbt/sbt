import sjsonnew.BasicJsonProtocol._

val copyFile = taskKey[Int]("dummy task")
copyFile / fileInputs += baseDirectory.value.toGlob / "base" / "*.txt"
copyFile / fileOutputs += baseDirectory.value.toGlob / "out" / "*.txt"

copyFile := Def.task {
  val prev = copyFile.previous
  prev match {
    case Some(v: Int) if (copyFile / changedInputFiles).value.isEmpty => v
    case _ =>
      (copyFile / changedInputFiles).value.foreach { p =>
        val outdir = baseDirectory.value / "out"
        IO.createDirectory(baseDirectory.value / "out")
        IO.copyFile(p.toFile, outdir / p.getFileName.toString)
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
