val foo = taskKey[Unit]("foo")
foo := println("foo")

foo / watchOnIteration := { _ => sbt.nio.Watch.CancelWatch }
addCommandAlias("bar", "foo")
addCommandAlias("baz", "foo")
