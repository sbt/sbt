scalaVersion := "3.7.4"

// Enable forked console
Compile / console / fork := true

// Test that javaOptions are passed to the forked console
Compile / console / javaOptions += "-Xmx256m"

// Test that initialCommands work in forked console
Compile / console / initialCommands := """println("Forked console initialized!")"""

// Test that cleanupCommands work in forked console
Compile / console / cleanupCommands := """println("Forked console cleanup!")"""
