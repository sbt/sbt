ThisBuild / doc / scalacOptions += "-Xsomething"

lazy val lintBuildTest = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    lintBuildTest := {
      val state = Keys.state.value
      val includeKeys = (includeLintKeys in Global).value map { _.scopedKey.key.label }
      val excludeKeys = (excludeLintKeys in Global).value map { _.scopedKey.key.label }
      val result = sbt.internal.LintBuild.lint(state, includeKeys, excludeKeys)
      assert(result.size == 1)
      assert(result(0)._2 == "ThisBuild / doc / scalacOptions", result(0)._2)
    }
  )

lazy val app = project
