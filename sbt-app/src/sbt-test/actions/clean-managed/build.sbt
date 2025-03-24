import sbt.nio.file.Glob

name := "clean-managed"
scalaVersion := "3.3.1"
Compile / sourceGenerators += {
  Def.task {
    val files = Seq(sourceManaged.value / "foo.txt", sourceManaged.value / "bar.txt")
    files.foreach(IO.touch(_))
    files
  }
}

cleanKeepGlobs += Glob(sourceManaged.value, "bar.txt")
