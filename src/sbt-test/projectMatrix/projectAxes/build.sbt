lazy val scala213 = "2.13.3"
lazy val scala212 = "2.12.12"
lazy val check = taskKey[Unit]("")
lazy val platformTest = settingKey[String]("")
lazy val configTest = settingKey[String]("")


lazy val config12 = ConfigAxis("Config1_2", "config1.2")
lazy val config13 = ConfigAxis("Config1_3", "config1.3")

lazy val root = (project in file("."))
  .aggregate((core.projectRefs ++ custom.projectRefs):_*)

lazy val core = (projectMatrix in file("core"))
  .settings(
    check := {
       assert(platformTest.value.endsWith("-platform"))
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala213, scala212))
  .jsPlatform(scalaVersions = Seq(scala212))
  .settings(platformSettings)

lazy val custom =
  (projectMatrix in file("custom"))
  .customRow(
    scalaVersions = Seq(scala212),
    axisValues = Seq(config13, VirtualAxis.jvm),
    _.settings()
  )
  .settings(platformSettings)
  .settings(customSettings)
  .settings(
    check := {
      assert(platformTest.value.endsWith("-platform"))
      assert(configTest.value.startsWith("config for"))
    }
  )


lazy val platformSettings = Seq[Def.Setting[_]](
    platformTest := {
      if(virtualAxes.value.contains(sbt.VirtualAxis.js)) "js-platform"
      else if(virtualAxes.value.contains(sbt.VirtualAxis.jvm)) "jvm-platform"
      else throw new RuntimeException(s"Something must be wrong (built-in platforms test) - virtualAxes value is ${virtualAxes.value}")
    }
)

lazy val customSettings = Seq[Def.Setting[_]](
  configTest := {
    if(virtualAxes.value.contains(config12)) "config for 1.2"
    else if (virtualAxes.value.contains(config13)) "config for 1.3"
    else throw new RuntimeException(s"Something must be wrong (custom axis test ) - virtualAxes value is ${virtualAxes.value}")
  }
)
