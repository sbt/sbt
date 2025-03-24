// lazy val root = (project in file("."))
//   .aggregate(core.projectRefs ++ app.projectRefs: _*)
//   .settings(
//   )

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .jsPlatform(scalaVersions = Seq("2.12.8", "2.11.12"))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app"
  )
  .jsPlatform(scalaVersions = Seq("2.12.8"))
