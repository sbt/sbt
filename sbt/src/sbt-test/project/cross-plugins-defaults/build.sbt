val baseSbt = "0.13"

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
    },

    TaskKey[Unit]("check2") := {
      val crossV = (sbtVersion in pluginCrossBuild).value
      val sv = projectID.value.extraAttributes("e:scalaVersion")
      assert(sbtVersion.value startsWith baseSbt, s"Wrong sbt version: ${sbtVersion.value}")
      assert(sv == "2.12", s"Wrong e:scalaVersion: $sv")
      assert(scalaBinaryVersion.value == "2.12", s"Wrong Scala binary version: ${scalaBinaryVersion.value}")
      assert(crossV startsWith "1.0.", s"Wrong `sbtVersion in pluginCrossBuild`: $crossV")
    }
  )
