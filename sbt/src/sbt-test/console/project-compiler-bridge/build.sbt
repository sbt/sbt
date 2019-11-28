scalaVersion := "2.13.1"

// Send some bogus initial command so that it doesn't get stuck.
// The task itself will still succeed.
Compile / consoleProject / initialCommands := "bail!"
