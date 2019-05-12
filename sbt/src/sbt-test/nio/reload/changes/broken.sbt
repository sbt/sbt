val foo = taskKey[Unit]("broken task")
foo := { throw new IllegalStateException("foo") }

Global / onChangedBuildSource := ReloadOnSourceChanges
