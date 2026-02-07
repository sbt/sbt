// Test for issue #6624: autoStartServer should not trigger lintUnused warning
Global / autoStartServer := false

lazy val root = (project in file("."))
  .settings(
    name := "lint-autostartserver",
    scalaVersion := "2.13.12",
    TaskKey[Unit]("check") := {
      val state = Keys.state.value
      val includeKeys = (Global / lintIncludeFilter).value
      val excludeKeys = (Global / lintExcludeFilter).value
      val result = sbt.internal.LintUnused.lintUnused(state, includeKeys, excludeKeys)
      // autoStartServer should not appear in the lint results
      // Check for both "Global / autoStartServer" and "autoStartServer" patterns
      val autoStartServerWarnings = result.filter { case (_, key, _) =>
        key.contains("autoStartServer")
      }
      if (autoStartServerWarnings.nonEmpty) {
        sys.error(s"autoStartServer should not trigger lintUnused warnings, but found: ${autoStartServerWarnings.mkString(", ")}")
      }
      streams.value.log.info("✓ autoStartServer correctly excluded from lintUnused warnings")
    }
  )
