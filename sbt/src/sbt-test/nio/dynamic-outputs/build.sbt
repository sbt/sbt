import java.nio.file.{ Path, Paths }

val foo = taskKey[Seq[Path]]("Copy files")
foo / fileInputs += baseDirectory.value.toGlob / "base" / "*.txt"
foo / target := baseDirectory.value / "out"
foo := {
  val out = baseDirectory.value / "out"
  foo.inputFiles.map { p =>
    val f = p.toFile
    val target = out / f.getName
    IO.copyFile (f, target)
    target.toPath
  }
}

val checkOutputFiles = inputKey[Unit]("check output files")
checkOutputFiles := {
  val actual: Seq[Path] =
    fileTreeView.value.list(baseDirectory.value.toGlob / "out" / **).map(_._1.getFileName).toList
  Def.spaceDelimited("").parsed.head match {
    case "empty" => assert(actual.isEmpty)
    case fileName => assert(actual == Paths.get(fileName) :: Nil)
  }
}
