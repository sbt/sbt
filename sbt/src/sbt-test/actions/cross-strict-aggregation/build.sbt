lazy val root = (project in file("."))
  .aggregate(core, module)

lazy val core = (project in file("core"))
  .settings(
    scalaVersion := "2.12.6",
    crossScalaVersions := Seq("2.11.11", "2.12.6"))

lazy val module = (project in file("module"))
  .dependsOn(core)
  .settings(
    scalaVersion := "2.12.6",
    crossScalaVersions := Seq("2.12.6"))
