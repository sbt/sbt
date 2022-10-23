name := "Mappings Test"

version := "0.2"

Compile / packageBin / mappings ++= {
  val test = file("test")
  Seq(
    test -> "test1",
    test -> "test1",
    test -> "test2"
  )
}

lazy val unzipPackage = taskKey[Unit]("extract jar file")
unzipPackage := {
  IO.unzip((Compile / packageBin).value, target.value / "extracted")
}
