lazy val scala213 = "2.13.3"
lazy val scala212 = "2.12.12"
lazy val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .aggregate(core.projectRefs ++ app.projectRefs: _*)
  .settings(
  )

lazy val app = (projectMatrix in file("app"))
  .aggregate(core, intf)
  .dependsOn(core, intf)
  .settings(
    name := "app"
  )
  .jvmPlatform(scalaVersions = Seq(scala213))

lazy val core = (projectMatrix in file("core"))
  .settings(
    check := {
      assert(moduleName.value == "core", s"moduleName is ${moduleName.value}")
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala213, scala212))

lazy val intf = (projectMatrix in file("intf"))
  .settings(
    check := {
      assert(moduleName.value == "intf", s"moduleName is ${moduleName.value}")
    },
  )
  .jvmPlatform(autoScalaLibrary = false)

lazy val core213 = core.jvm(scala213)
