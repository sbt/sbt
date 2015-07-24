name := "test-parent-pom"

val localMavenRepo = file("local-repo")
                                                                                                                                                                                                                                                                                                                        val cleanExampleCache = taskKey[Unit]("Cleans the example cache.")

resolvers +=
  MavenRepository("Maven2 Local Test", localMavenRepo.toURI.toString)


libraryDependencies +=
  "com.example" % "example-child" % "1.0-SNAPSHOT"

libraryDependencies += "org.apache.geronimo.specs" % "geronimo-jta_1.1_spec" % "1.1.1"

version := "1.0-SNAPSHOT"


cleanExampleCache := {
    ivySbt.value.withIvy(streams.value.log) { ivy =>
      val cacheDir = ivy.getSettings.getDefaultRepositoryCacheBasedir
      // TODO - Is this actually ok?
      IO.delete(cacheDir / "com.example")
    }
}

val checkIvyXml = taskKey[Unit]("Checks the ivy.xml transform was correct")



checkIvyXml := {
  ivySbt.value.withIvy(streams.value.log) { ivy =>
    val cacheDir = ivy.getSettings.getDefaultRepositoryCacheBasedir
    // TODO - Is this actually ok?
    val xmlFile  =
      cacheDir / "org.apache.geronimo.specs" / "geronimo-jta_1.1_spec" / "ivy-1.1.1.xml"
      //cacheDir / "com.example" / "example-child" / "ivy-1.0-SNAPSHOT.xml"
    val lines = IO.read(xmlFile)
    if(lines.isEmpty) sys.error(s"Unable to read $xmlFile, could not resolve geronimo...")
    // Note: We do not do this if the maven plguin is enabled, because there is no rewrite of ivy.xml, extra attribtues
    // are handled in a different mechanism.  This is a hacky mechanism to detect that.
    val isMavenResolver = updateOptions.value.resolverConverter != PartialFunction.empty
    if(!isMavenResolver) assert(lines contains "xmlns:e", s"Failed to appropriately modify ivy.xml file for sbt extra attributes!\n$lines")
  }
}
