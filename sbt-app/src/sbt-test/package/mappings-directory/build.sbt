name := "Mappings Test"

scalaVersion := "3.3.1"
version := "0.2"

Compile / packageBin / mappings ++= {
  val converter = fileConverter.value
  Mapper.directory(file("test"))(using converter)
}

lazy val unzipPackage = taskKey[Unit]("extract jar file")
unzipPackage := {
  val converter = fileConverter.value
  val p = converter.toPath((Compile / packageBin).value)
  IO.unzip(p.toFile(), target.value / "extracted")
}
