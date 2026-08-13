lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core"
  )
  .jsPlatform(scalaVersions = Seq("2.12.21", "2.13.18"))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app"
  )
  .jsPlatform(scalaVersions = Seq("2.12.21"))
