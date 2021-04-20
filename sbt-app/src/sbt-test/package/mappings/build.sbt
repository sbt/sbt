name := "Mappings Test"

version := "0.2"

mappings in (Compile, packageBin) ++= {
  val test = file("test")
  Seq(
    test -> "test1",
    test -> "test1",
    test -> "test2"
  )
}

lazy val unzipPackage = taskKey[Unit]("extract jar file")
unzipPackage := {
  IO.unzip((packageBin in Compile).value, target.value / "extracted")
}