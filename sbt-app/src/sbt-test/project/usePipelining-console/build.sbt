// Regression test for #8921: pipelining flags must not leak into console/scalacOptions.

ThisBuild / usePipelining := true
ThisBuild / scalaVersion  := "3.8.1"

val checkConsoleScalacOptions = taskKey[Unit](
  "Fails if console/scalacOptions still contains pipelining flags (-Ypickle-java / -Ypickle-write)"
)

lazy val subproject = project
  .in(file("modules/subproject"))
  .settings(
    checkConsoleScalacOptions := {
      val opts = (Compile / console / scalacOptions).value
      val bad  = opts.filter(o => o == "-Ypickle-java" || o == "-Ypickle-write")
      if (bad.nonEmpty)
        sys.error(s"pipelining flags must not reach the REPL, found: $bad")
    }
  )

lazy val root = project
  .in(file("."))
  .dependsOn(subproject)
  .aggregate(subproject)
  .settings(
    checkConsoleScalacOptions := {
      val opts = (Compile / console / scalacOptions).value
      val bad  = opts.filter(o => o == "-Ypickle-java" || o == "-Ypickle-write")
      if (bad.nonEmpty)
        sys.error(s"pipelining flags must not reach the REPL, found: $bad")
    }
  )
