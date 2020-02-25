ThisBuild / doc / scalacOptions += "-Xsomething"

lazy val lintBuildTest = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    lintBuildTest := {
      val state = Keys.state.value
      val includeKeys = (includeLintKeys in Global).value map { _.scopedKey.key.label }
      val excludeKeys = (excludeLintKeys in Global).value map { _.scopedKey.key.label }
      val result = sbt.internal.LintUnused.lintUnused(state, includeKeys, excludeKeys)
      result foreach {
        case (_, "ThisBuild / doc / scalacOptions", _) => ()
        case (_, "app / globalAutoPluginSetting", _)   => ()
        case (_, "root / globalAutoPluginSetting", _)  => ()
        case (_, str, _)                               => sys.error(str)
      }
    }
  )

lazy val app = project
