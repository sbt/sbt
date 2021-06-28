lazy val check = taskKey[Unit]("Runs the check")

lazy val root = (project in file("."))
  .settings(
    autoScalaLibrary := false, 
    libraryDependencies += "org.webjars.npm" % "is-odd" % "2.0.0",
    dependencyOverrides += "org.webjars.npm" % "is-number" % "5.0.0",
    check := {
      val cp = (Compile / externalDependencyClasspath).value.map {_.data.getName}.sorted
      if (!(cp contains "is-number-5.0.0.jar")) {
        sys.error("is-number-5.0.0 not found when it should be included: " + cp.toString)
      }
    }
  )
