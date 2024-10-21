lazy val scala213 = "2.13.15"
lazy val scala3 = "3.4.2"
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
  .jvmPlatform(scalaVersions = Seq(scala3))

lazy val core = (projectMatrix in file("core"))
  .settings(
    check := {
      assert(moduleName.value == "core", s"moduleName is ${moduleName.value}")
      assert(projectMatrixBaseDirectory.value == file("core"))
    },
  )
  .jvmPlatform(scalaVersions = Seq(scala213, scala3))

lazy val intf = (projectMatrix in file("intf"))
  .settings(
    check := {
      assert(moduleName.value == "intf", s"moduleName is ${moduleName.value}")
      assert(projectMatrixBaseDirectory.value == file("intf"))
    },
  )
  .jvmPlatform(autoScalaLibrary = false)

lazy val core213 = core.jvm(scala213)
