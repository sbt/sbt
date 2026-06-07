scalaVersion := "3.8.4"

organization := "com.example"

@transient
lazy val check = taskKey[Unit]("extract jar file")

lazy val a = (project in file("a"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "a",
    check := Def.uncached {
      val converter = fileConverter.value
      val p = converter.toPath((Compile / packageBin).value)
      val outDir = target.value / "extracted"
      IO.unzip(p.toFile(), outDir)
      val expectedResource = outDir / "sbt" / "sbt.autoplugins"
      if !expectedResource.exists() then
        sys.error(s"$expectedResource doesn't exist")
      val content = IO.read(outDir / "sbt" / "sbt.autoplugins").trim
      assert(content == "A", s"expected A, but content was '$content'")
    },
  )
