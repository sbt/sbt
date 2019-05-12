val foo = taskKey[Unit]("working task")
foo := { println("foo") }

Global / onChangedBuildSource := ReloadOnSourceChanges
