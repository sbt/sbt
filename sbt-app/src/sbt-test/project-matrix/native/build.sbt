lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core",
    Compile / run / mainClass := Some("a.CoreMain")
  )
  .nativePlatform(scalaVersions = Seq("2.13.18"))

lazy val app = (projectMatrix in file("app"))
  .dependsOn(core)
  .settings(
    name := "app",
    Compile / run / mainClass := Some("a.App")
  )
  .nativePlatform(scalaVersions = Seq("2.13.18"))
