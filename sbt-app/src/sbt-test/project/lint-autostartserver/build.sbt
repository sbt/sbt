// Test for issue #6624: autoStartServer should not trigger lintUnused warning
Global / autoStartServer := false

@transient
lazy val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    check := Def.uncached {
      val state = Keys.state.value
      val includeKeys = (Global / lintIncludeFilter).value
      val excludeKeys = (Global / lintExcludeFilter).value
      val result = sbt.internal.LintUnused.lintUnused(state, includeKeys, excludeKeys)
      // autoStartServer should not appear in the lint results
      val autoStartServerWarnings = result.filter { case (_, key, _) =>
        key.contains("autoStartServer")
      }
      assert(
        autoStartServerWarnings.isEmpty,
        s"autoStartServer should not trigger lintUnused warnings, but found: ${autoStartServerWarnings.map(_._2).mkString(", ")}"
      )
    }
  )
