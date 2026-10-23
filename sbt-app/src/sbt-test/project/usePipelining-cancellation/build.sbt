// Test for issue #7973: CancellationException should not be thrown on compile errors with usePipelining
ThisBuild / usePipelining := true

lazy val root = (project in file("."))
  .settings(
    name := "usePipelining-cancellation",
    scalaVersion := "2.13.15"
  )
