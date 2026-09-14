lazy val check = taskKey[Unit]("Checks pluginCrossBuild / sbtVersion")

lazy val root = (project in file("."))
  .enablePlugins(ConcreteSbtPlugin)
  .settings(
    scalaVersion := "2.12.21",
    check := {
      val actual = (pluginCrossBuild / sbtVersion).value
      val expected = "1.12.12"
      assert(actual == expected, s"Expected pluginCrossBuild / sbtVersion to be $expected, got $actual")

      val defaultActual = (defaultPlugin / pluginCrossBuild / sbtVersion).value
      val defaultExpected = "1.5.8"
      assert(
        defaultActual == defaultExpected,
        s"Expected default pluginCrossBuild / sbtVersion to be $defaultExpected, got $defaultActual"
      )

      val sameAsRunningActual = (sameAsRunning / pluginCrossBuild / sbtVersion).value
      val sameAsRunningExpected = sbtVersion.value
      assert(
        sameAsRunningActual == sameAsRunningExpected,
        s"Expected explicit pluginCrossBuild / sbtVersion to be $sameAsRunningExpected, got $sameAsRunningActual"
      )
    }
  )

lazy val defaultPlugin = (project in file("default-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    scalaVersion := "2.12.21"
  )

lazy val sameAsRunning = (project in file("same-as-running"))
  .enablePlugins(SameAsRunningPlugin)
  .settings(
    scalaVersion := "2.12.21"
  )
