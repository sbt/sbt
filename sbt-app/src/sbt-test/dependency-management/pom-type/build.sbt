lazy val checkPom = taskKey[Unit]("check pom to ensure no <type> sections are generated")

lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.13.16",
    libraryDependencies += { ("com.typesafe" % "config" % "1.4.3").withSources().withJavadoc() },
    libraryDependencies += { ("org.slf4j" % "slf4j-api" % "2.0.16").intransitive() },
    checkPom := {
      val converter = fileConverter.value
      val pomFile = makePom.value
      val pom = xml.XML.loadFile(converter.toPath(pomFile).toFile)
      val tpe = pom \\ "type"
      if (tpe.nonEmpty) {
        sys.error("Expected no <type> sections, got: " + tpe + " in \n\n" + pom)
      }
      val ur = update.value
      val dir = (update / streams).value.cacheDirectory / "out"
      val lines = IO.readLines(dir)
      val hasError = lines.exists(line => line.contains("Found intransitive dependency "))
      assert(hasError, s"Failed to detect intransitive dependencies, got: ${lines.mkString("\n")}")
    },
  )
