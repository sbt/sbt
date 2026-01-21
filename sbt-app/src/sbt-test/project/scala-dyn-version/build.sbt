ThisBuild / scalaVersion := "3-latest.candidate"

lazy val checkDynVersion = taskKey[Unit]("Check that scalaDynVersion resolves correctly")

lazy val p1 = project
  .settings(
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.6.3"
  )

lazy val p2 = project
  .dependsOn(p1)
  .settings(
    checkDynVersion := {
      val log = streams.value.log
      val sv = scalaVersion.value
      val dynSv = scalaDynVersion.value
      val binSv = scalaBinaryVersion.value

      log.info(s"scalaVersion: $sv")
      log.info(s"scalaDynVersion: $dynSv")
      log.info(s"scalaBinaryVersion: $binSv")

      // scalaVersion should be the dynamic pattern
      assert(sv == "3-latest.candidate", s"Expected scalaVersion '3-latest.candidate', got '$sv'")

      // scalaDynVersion should resolve to a concrete RC version (e.g., "3.8.1-RC1")
      assert(dynSv.matches("""\d+\.\d+\.\d+-RC\d+"""), s"Expected scalaDynVersion to be a concrete RC version, got '$dynSv'")

      // scalaBinaryVersion should be "3"
      assert(binSv == "3", s"Expected scalaBinaryVersion '3', got '$binSv'")

      log.success("All checks passed!")
    }
  )
