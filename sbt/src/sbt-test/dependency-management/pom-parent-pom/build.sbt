val checkIvyXml = taskKey[Unit]("Checks the ivy.xml transform was correct")

lazy val root = (project in file(".")).
  settings(
    name := "test-parent-pom",
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
    resolvers += MavenCache("Maven2 Local Test", baseDirectory.value / "local-repo"),
    libraryDependencies += "com.example" % "example-child" % "1.0-SNAPSHOT",
    libraryDependencies += "org.apache.geronimo.specs" % "geronimo-jta_1.1_spec" % "1.1.1",
    version := "1.0-SNAPSHOT",
    autoScalaLibrary := false,
    checkIvyXml := {
      val resolverConverter = updateOptions.value.resolverConverter
      ivySbt.value.withIvy(streams.value.log) { ivy =>
        val cacheDir = ivy.getSettings.getDefaultRepositoryCacheBasedir
        val xmlFile  =
          cacheDir / "org.apache.geronimo.specs" / "geronimo-jta_1.1_spec" / "ivy-1.1.1.xml"
        val lines = IO.read(xmlFile)
        if(lines.isEmpty) sys.error(s"Unable to read $xmlFile, could not resolve geronimo...")
        // Note: We do not do this if the maven plugin is enabled, because there is no rewrite of ivy.xml, extra attributes
        // are handled in a different mechanism.  This is a hacky mechanism to detect that.
        val isMavenResolver = resolverConverter != PartialFunction.empty
        if(!isMavenResolver) assert(lines contains "xmlns:e", s"Failed to appropriately modify ivy.xml file for sbt extra attributes!\n$lines")

        val xmlFile2 = cacheDir / "com.example" / "example-child" / "ivy-1.0-SNAPSHOT.xml"
        val lines2 = IO.read(xmlFile2)
        if (!isMavenResolver) {
          assert(lines2 contains "Apache-2.0", s"Failed to roll up license from the parent POM!\n$lines2")
        }
      }
    }
  )
