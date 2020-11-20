ThisBuild / doc / scalacOptions += "-Xsomething"

ThisBuild / shellPrompt := { state => "sbt> " }

// watch related settings
ThisBuild / sbt.nio.Keys.watchTriggers := Seq()
ThisBuild / sbt.nio.Keys.watchPersistFileStamps := true

lazy val lintBuildTest = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    lintBuildTest := {
      val state = Keys.state.value
      val includeKeys = (lintIncludeFilter in Global).value
      val excludeKeys = (lintExcludeFilter in Global).value
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
