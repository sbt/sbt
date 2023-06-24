scalaVersion := "2.13.11"

// Send some bogus initial command so that it doesn't get stuck.
// The task itself will still succeed.
consoleProject / initialCommands := "bail!"
