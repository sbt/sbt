ThisBuild / scalaVersion := "3.7.4"

lazy val root = project.in(file(".")).settings(
  // Verify that consoleProject bindings are accessible in the Scala 3 REPL.
  // These assertions run as initialCommands; if any binding is null, the REPL fails.
  consoleProject / initialCommands :=
    """assert(currentState != null, "currentState binding missing")
      |assert(extracted != null, "extracted binding missing")
      |assert(cpHelpers != null, "cpHelpers binding missing")
      |""".stripMargin,
)
