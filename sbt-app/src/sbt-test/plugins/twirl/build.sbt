
lazy val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .enablePlugins(TwirlPlugin)
  .settings(
    check := {
      val templates = (Compile / twirlCompileTemplates / sources).value
      assert(templates.nonEmpty)
      assert(!templates.exists(_.toString.contains("hidden")))
    }
  )
