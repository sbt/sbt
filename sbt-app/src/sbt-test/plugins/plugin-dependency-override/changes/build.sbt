
lazy val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .enablePlugins(DependentPlugin)
  .settings(
    check := {
      val v = (Compile / pluginVersion).value
      if(v == "1.0.0") {} else sys.error(s"selected version is not 1.0.0: $v")
    }
  )
