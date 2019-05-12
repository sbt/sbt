// lazy val root = (project in file("."))
//   .aggregate(core.projectRefs ++ app.projectRefs: _*)
//   .settings(
//   )

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .nativePlatform(scalaVersions = Seq("2.11.12", "2.11.11"))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app"
  )
  .nativePlatform(scalaVersions = Seq("2.11.12"))
