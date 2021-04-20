import java.nio.file.{ Files, Path }

val outputTask = taskKey[Seq[Path]]("A task that generates outputs")
outputTask := {
  val dir = Files.createDirectories(streams.value.cacheDirectory.toPath)
  Seq("foo.txt" -> "foo", "bar.txt" -> "bar").map { case (name, content) =>
    Files.write(dir/ name, content.getBytes)
  } :+ dir
}

val checkOutputs = inputKey[Unit]("check outputs")
checkOutputs := {
  val expected = Def.spaceDelimited("").parsed.map {
    case "base" => (outputTask / streams).value.cacheDirectory.toPath
    case f => (outputTask / streams).value.cacheDirectory.toPath / f
  }
  assert((outputTask / allOutputFiles).value.toSet == expected.toSet)
}

val barFilter = settingKey[PathFilter]("A filter for the bar.txt file")
barFilter := ** / "bar.txt"
