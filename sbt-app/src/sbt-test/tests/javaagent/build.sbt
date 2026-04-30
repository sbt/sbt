scalaVersion := "2.13.18"

lazy val core = project
  .settings(
    libraryDependencies += "org.scalatest" %% "scalatest-funspec" % "3.2.20" % Test,
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Raw,
    Test / javaOptions += {
      val agentJar = fileConverter.value.toPath(
        (agent / Compile / packageBin).value
      ).toFile.getCanonicalPath
      s"-javaagent:${agentJar}"
    }
  )
  .dependsOn(agent)

lazy val agent = project
  .settings(
    autoScalaLibrary := false,
    packageOptions += Package.ManifestAttributes(
      "Premain-Class" -> "example.JavaAgentMain"
    )
  )
  .dependsOn(lib)

lazy val lib = project
  .settings(
    autoScalaLibrary := false
  )
