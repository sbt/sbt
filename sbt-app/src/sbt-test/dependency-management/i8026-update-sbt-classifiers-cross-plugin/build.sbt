// Test for https://github.com/sbt/sbt/issues/8026
// When building sbt plugins with explicit scalaVersion set,
// updateSbtClassifiers should use the correct Scala version for the sbt version.

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "test-sbt-cross-build",

    // Explicitly set scala version - this is what caused the issue in #8026
    // When scalaVersion is explicitly set, updateSbtClassifiers was using the
    // launcher's Scala version instead of the plugin's Scala version.
    scalaVersion := "2.12.21",

    // Task to verify the scala version used for updateSbtClassifiers is correct
    // We use pluginCrossBuild / scalaVersion as the expected value since that's
    // what should be used for sbt plugins
    TaskKey[Unit]("checkScalaVersion") := {
      val expectedScalaVersion = (pluginCrossBuild / scalaVersion).value
      val updateSbtScalaVersion = (updateSbtClassifiers / scalaVersion).value

      assert(
        updateSbtScalaVersion == expectedScalaVersion,
        s"Wrong Scala version in updateSbtClassifiers scope. " +
          s"Expected '$expectedScalaVersion' but got '$updateSbtScalaVersion'"
      )
    }
  )
