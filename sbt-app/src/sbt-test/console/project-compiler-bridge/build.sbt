scalaVersion := "2.13.12"

// Send some bogus initial command so that it doesn't get stuck.
// The task itself will still succeed.
consoleProject / initialCommands := "bail!"
