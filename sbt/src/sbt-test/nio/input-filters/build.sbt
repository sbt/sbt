import java.nio.file.{ Files, Path }

val copyPaths = taskKey[Seq[Path]]("Copy paths")
copyPaths / fileInputs += baseDirectory.value.toGlob / "inputs" / *
copyPaths := {
  val outFile = streams.value.cacheDirectory
  IO.delete(outFile)
  val out = Files.createDirectories(outFile.toPath)
  copyPaths.inputFiles.map { path =>
    Files.write(out / path.getFileName.toString, Files.readAllBytes(path))
  }
}

val checkPaths = inputKey[Unit]("check paths")
checkPaths := {
  val expectedFileNames = Def.spaceDelimited().parsed.toSet
  val actualFileNames = copyPaths.outputFiles.map(_.getFileName.toString).toSet
  assert(expectedFileNames == actualFileNames)

}

val newFilter = settingKey[PathFilter]("Works around quotations not working in scripted")
newFilter := HiddenFileFilter.toNio || "**/bar.txt"

val fooFilter = settingKey[PathFilter]("A filter for the bar.txt file")
fooFilter := ** / ".foo.txt"

Global / onLoad := { s: State =>
  if (scala.util.Properties.isWin) {
    val path = s.baseDir.toPath / "inputs" / ".foo.txt"
    Files.setAttribute(path, "dos:hidden", true)
  }
  s
}
