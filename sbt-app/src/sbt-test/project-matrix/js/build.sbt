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

lazy val check = taskKey[Unit]("")

// a row that customRow builds with the js axis
lazy val bare = (projectMatrix in file("bare"))
  .customRow(
    true,
    Seq("2.13.18"),
    Seq(VirtualAxis.js),
    _.settings(check := assert(platform.value == "jvm", platform.value)),
  )
