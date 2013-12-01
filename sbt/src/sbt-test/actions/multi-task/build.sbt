lazy val a = taskKey[Unit]("a")

lazy val b = taskKey[Unit]("b")

a := IO.touch(baseDirectory.value / "a")

b := IO.touch(baseDirectory.value / "b")
