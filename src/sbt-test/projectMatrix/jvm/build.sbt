// lazy val root = (project in file("."))
//   .aggregate(core.projectRefs ++ app.projectRefs: _*)
//   .settings(
//   )

lazy val core = (projectMatrix in file("core"))
  .scalaVersions("2.12.6", "2.11.12")
  .settings(
    name := "core"
  )
  .jvmPlatform()

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .scalaVersions("2.12.6")
  .settings(
    name := "app"
  )
  .jvmPlatform()
