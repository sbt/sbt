lazy val scala3_LTS = "3.3.5"
lazy val scala3_current = "3.7.4"
lazy val check = taskKey[Unit]("")

organization := "com.example"
version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate((core.projectRefs ++ app.projectRefs)*)
  .settings(
  )

lazy val app = (projectMatrix in file("app"))
  .aggregate(core, intf)
  .dependsOn(core, intf)
  .settings(
    name := "app",
  )
  .jvmPlatform(
    crossVersion = CrossVersion.full,
    scalaVersions = Seq(scala3_LTS, scala3_current),
  )

lazy val core = (projectMatrix in file("core"))
  .settings(
    check := {
      assert(moduleName.value == "core", s"moduleName is ${moduleName.value}")
      assert(projectMatrixBaseDirectory.value == file("core"))
      assert(projectID.value.crossVersion == CrossVersion.full, s"crossVersion is ${projectID.value.crossVersion}")
    },
  )
  .jvmPlatform(
    crossVersion = CrossVersion.full,
    scalaVersions = Seq(scala3_LTS, scala3_current)
  )

lazy val intf = (projectMatrix in file("intf"))
  .settings(
    check := {
      assert(moduleName.value == "intf", s"moduleName is ${moduleName.value}")
      assert(projectMatrixBaseDirectory.value == file("intf"))
    },
  )
  .jvmPlatform(
    autoScalaLibrary = false,
    crossVersion = CrossVersion.disabled,
  )

lazy val core_3_LTS = core.jvm(scala3_LTS)
