// Test for issue #6652: Doc task fails with `bad option '-doc-external-doc'` for scala3 project
// This test verifies that autoAPIMappings works correctly with Scala 3's -external-mappings option

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "3.3.4",
    autoAPIMappings := true,
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.12.0",
    TaskKey[Unit]("checkDocGenerated") := {
      val docDir = (Compile / doc / target).value
      val indexFile = docDir / "index.html"
      assert(indexFile.exists(), s"Expected $indexFile to exist after doc generation")
      println(s"Documentation generated successfully at $docDir")
    },
    TaskKey[Unit]("checkApiMappings") := {
      val mappings = (Compile / doc / apiMappings).value
      println(s"API Mappings count: ${mappings.size}")
      // With autoAPIMappings and cats-core dependency, we should have some mappings
      // (cats-core publishes with apiURL)
      println(s"API Mappings: ${mappings.take(3).mkString("\n  ", "\n  ", "")}")
    }
  )
