val foo = taskKey[Unit]("foo")
foo := println("foo")

foo / watchOnIteration := { (_, _, _) => sbt.nio.Watch.CancelWatch }
addCommandAlias("bar", "foo")
addCommandAlias("baz", "foo")
