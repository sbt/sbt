// Regression test for #8921: console must work in a pipelined multi-project build.
// Before the fix, subproject/console threw a FileSystemException on Windows because
// the REPL driver tried to open early.jar while the pipelining build still held a lock.
// The root console would start but then print "Canceling execution..." on any input.
// We cannot run console non-interactively (the REPL blocks on stdin), so we verify
// the fix by asserting that pipelining flags are absent from console/scalacOptions.

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
