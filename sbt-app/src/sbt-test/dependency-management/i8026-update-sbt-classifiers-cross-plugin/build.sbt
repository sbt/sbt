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
    TaskKey[Unit]("checkScalaVersion") := {
      val sbtBinV = (pluginCrossBuild / sbtBinaryVersion).value
      val expectedScalaPrefix = sbtBinV match {
        case v if v.startsWith("0.13") => "2.10"
        case v if v.startsWith("1.")   => "2.12"
        case v if v.startsWith("2.")   => "3"
        case _                         => sys.error(s"Unexpected sbt binary version: $sbtBinV")
      }

      // Get the scala version that would be used for updateSbtClassifiers
      val updateSbtScalaVersion = (updateSbtClassifiers / scalaVersion).value
      val updateSbtScalaBinVersion = (updateSbtClassifiers / scalaBinaryVersion).value

      assert(
        updateSbtScalaBinVersion.startsWith(expectedScalaPrefix),
        s"Wrong Scala binary version in updateSbtClassifiers scope. " +
          s"Expected to start with '$expectedScalaPrefix' but got '$updateSbtScalaBinVersion' " +
          s"(full version: $updateSbtScalaVersion) for sbt binary version '$sbtBinV'"
      )
    }
  )
