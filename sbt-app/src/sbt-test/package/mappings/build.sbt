name := "Mappings Test"

scalaVersion := "3.3.1"
version := "0.2"

Compile / packageBin / mappings ++= {
  val converter = fileConverter.value
  val test = converter.toVirtualFile(file("test").toPath)
  Seq(
    test -> "test1",
    // not sure why we allowed duplicates here
    // test -> "test1",
    test -> "test2"
  )
}

lazy val unzipPackage = taskKey[Unit]("extract jar file")
unzipPackage := {
  val converter = fileConverter.value
  val p = converter.toPath((Compile / packageBin).value)
  IO.unzip(p.toFile(), target.value / "extracted")
}
