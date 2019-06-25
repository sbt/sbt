// lazy val root = (project in file("."))
//   .aggregate(core.projectRefs ++ app.projectRefs: _*)
//   .settings(
//   )

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core",
    mainClass in (Compile, run) := Some("a.CoreMain")
  )
  .nativePlatform(scalaVersions = Seq("2.11.12"))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app",
    mainClass in (Compile, run) := Some("a.App")
  )
  .nativePlatform(scalaVersions = Seq("2.11.12"))
