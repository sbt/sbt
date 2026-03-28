// Regression test for #8921: console must work in a pipelined multi-project build.
// Before the fix, `subproject/console` threw a FileSystemException on Windows because
// the REPL driver tried to open early.jar while the pipelining build still held a lock.
// The root `console` would start but then print "Canceling execution…" on any input.

ThisBuild / usePipelining := true
ThisBuild / scalaVersion := "3.8.1"

lazy val subproject = project
  .in(file("modules/subproject"))
  .settings(
    console / initialCommands := ":quit"
  )

lazy val root = project
  .in(file("."))
  .dependsOn(subproject)
  .aggregate(subproject)
  .settings(
    console / initialCommands := ":quit"
  )
