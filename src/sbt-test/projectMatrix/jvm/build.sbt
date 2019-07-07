lazy val check = taskKey[Unit]("")

// lazy val root = (project in file("."))
//   .aggregate(core.projectRefs ++ app.projectRefs: _*)
//   .settings(
//   )

lazy val core = (projectMatrix in file("core"))
  .settings(
    check := {
      assert(moduleName.value == "core", s"moduleName is ${moduleName.value}")
    },
  )
  .jvmPlatform(scalaVersions = Seq("2.12.8", "2.11.12"))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app"
  )
  .jvmPlatform(scalaVersions = Seq("2.12.8"))
