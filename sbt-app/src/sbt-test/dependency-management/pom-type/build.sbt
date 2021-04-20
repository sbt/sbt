lazy val checkPom = taskKey[Unit]("check pom to ensure no <type> sections are generated")

lazy val root = (project in file(".")).
  settings(
    scalaVersion := "2.10.6",
    libraryDependencies += "org.scala-tools.sbinary" %% "sbinary" % "0.4.1" withSources() withJavadoc(),
    libraryDependencies += "org.scala-sbt" % "io" % "0.13.8" intransitive(),
    checkPom := {
      val pomFile = makePom.value
      val pom = xml.XML.loadFile(pomFile)
      val tpe = pom \\ "type"
      if(tpe.nonEmpty) {
        sys.error("Expected no <type> sections, got: " + tpe + " in \n\n" + pom)
      }
      val ur = update.value
      val dir = (streams in update).value.cacheDirectory / "out"
      val lines = IO.readLines(dir)
      val hasError = lines exists { line => line contains "Found intransitive dependency "}
      assert(hasError, s"Failed to detect intransitive dependencies, got: ${lines.mkString("\n")}")
    },
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
