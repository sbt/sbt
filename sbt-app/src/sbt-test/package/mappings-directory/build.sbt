name := "Mappings Test"

scalaVersion := "3.3.1"
version := "0.2"

Compile / packageBin / mappings ++= {
  val converter = fileConverter.value
  Path.directory(file("test")).map { case (f,s) => converter.toVirtualFile(f.toPath) -> s }
}

lazy val unzipPackage = taskKey[Unit]("extract jar file")
unzipPackage := {
  val converter = fileConverter.value
  val p = converter.toPath((Compile / packageBin).value)
  IO.unzip(p.toFile(), target.value / "extracted")
}
