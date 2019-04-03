import sbt.nio.file.syntax._

Compile / sourceGenerators += Def.task {
  val files = Seq(sourceManaged.value / "foo.txt", sourceManaged.value / "bar.txt")
  files.foreach(IO.touch(_))
  files
}

cleanKeepGlobs += (sourceManaged.value / "bar.txt").toGlob
