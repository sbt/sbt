val baseSbt = "0.13"

val buildCrossList = List("2.10.6", "2.11.11", "2.12.2")
scalaVersion in ThisBuild := "2.10.6"
crossScalaVersions in ThisBuild := buildCrossList

lazy val root = (project in file("."))
  .settings(
    sbtPlugin := true,

    TaskKey[Unit]("check") := {
      val crossV = (sbtVersion in pluginCrossBuild).value
      val sv = projectID.value.extraAttributes("e:scalaVersion")
      assert(sbtVersion.value startsWith baseSbt, s"Wrong sbt version: ${sbtVersion.value}")
      assert(sv == "2.10", s"Wrong e:scalaVersion: $sv")
      assert(scalaBinaryVersion.value == "2.10", s"Wrong Scala binary version: ${scalaBinaryVersion.value}")
      assert(crossV startsWith "0.13", s"Wrong `sbtVersion in pluginCrossBuild`: $crossV")

      // crossScalaVersions in app should not be affected
      val appCrossScalaVersions = (crossScalaVersions in app).value.toList
      val appScalaVersion = (scalaVersion in app).value
      assert(appCrossScalaVersions == buildCrossList, s"Wrong `crossScalaVersions in app`: $appCrossScalaVersions")
      assert(appScalaVersion startsWith "2.10", s"Wrong `scalaVersion in app`: $appScalaVersion")
    },

    TaskKey[Unit]("check2") := {
      val crossV = (sbtVersion in pluginCrossBuild).value
      val sv = projectID.value.extraAttributes("e:scalaVersion")
      assert(sbtVersion.value startsWith baseSbt, s"Wrong sbt version: ${sbtVersion.value}")
      assert(sv == "2.12", s"Wrong e:scalaVersion: $sv")
      assert(scalaBinaryVersion.value == "2.12", s"Wrong Scala binary version: ${scalaBinaryVersion.value}")
      assert(crossV startsWith "1.0.", s"Wrong `sbtVersion in pluginCrossBuild`: $crossV")

      // ^^ should not affect app's crossScalaVersions
      val appCrossScalaVersions = (crossScalaVersions in app).value.toList
      val appScalaVersion = (scalaVersion in app).value
      assert(appCrossScalaVersions == buildCrossList, s"Wrong `crossScalaVersions in app`: $appCrossScalaVersions")
      assert(appScalaVersion startsWith "2.10", s"Wrong `scalaVersion in app`: $appScalaVersion")
    }
  )

lazy val app = (project in file("app"))
