lazy val foo = taskKey[Int]("foo")
lazy val bar = taskKey[Int]("bar")
lazy val sideEffect0 = taskKey[Unit]("side effect 0")
lazy val sideEffect1 = taskKey[Unit]("side effect 1")
lazy val sideEffect2 = taskKey[Unit]("side effect 2")
lazy val testFile = settingKey[File]("test file")
lazy val check = taskKey[Unit]("check")

lazy val root = project.
  settings(
    check := {
      val x = foo.value
      if (x == 1) ()
      else sys.error("foo did not return 1")
      val t = testFile.value
      val s = IO.read(t)
      val expected = "012"
      if (s == expected) ()
      else sys.error(s"""test.txt expected '$expected' but was '$s'.""")
    },
    testFile := target.value / "test.txt",
    sideEffect0 := {
      Thread.sleep(100)
      val t = testFile.value
      IO.append(t, "0")
    },
    sideEffect1 := {
      val t = testFile.value
      IO.append(t, "1")
    },
    sideEffect2 := {
      // check for deduplication of tasks
      val _ = sideEffect1.value
      val t = testFile.value
      IO.append(t, "2")
    },
    foo := Def.sequential(compile in Compile, sideEffect0, sideEffect1, sideEffect2, test in Test, bar).value,
    bar := 1
  )
