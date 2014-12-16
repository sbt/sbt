name := "test-parent-pom"

val localMavenRepo = file("local-repo")
                                                                                                                                                                                                                                                                                                                        val cleanExampleCache = taskKey[Unit]("Cleans the example cache.")

resolvers +=
  MavenRepository("Maven2 Local Test", localMavenRepo.toURI.toString)


libraryDependencies +=
  "com.example" % "example-child" % "1.0-SNAPSHOT"

version := "1.0-SNAPSHOT"


cleanExampleCache := {
    ivySbt.value.withIvy(streams.value.log) { ivy =>
      val cacheDir = ivy.getSettings.getDefaultRepositoryCacheBasedir
      // TODO - Is this actually ok?
      IO.delete(cacheDir / "com.example")
    }
}
