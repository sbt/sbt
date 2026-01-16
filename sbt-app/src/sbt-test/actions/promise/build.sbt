val midpoint = taskKey[PromiseWrap[Int]]("")
val longRunning = taskKey[Unit]("")
val midTask = taskKey[Unit]("")
val joinTwo = taskKey[Unit]("")
val output = settingKey[File]("")

lazy val root = (project in file("."))
  .settings(
    name := "promise",
    output := baseDirectory.value / "output.txt",
    midpoint := Def.uncached(Def.promise[Int]),
    longRunning := Def.uncached {
      val p = midpoint.value
      val st = streams.value
      IO.write(output.value, "start\n", append = true)
      Thread.sleep(100)
      p.success(5)
      Thread.sleep(100)
      IO.write(output.value, "end\n", append = true)
    },
    midTask := Def.uncached {
      val st = streams.value
      val x = midpoint.await.value
      IO.write(output.value, s"$x in the middle\n", append = true)
    },
    joinTwo := Def.uncached {
      val x = longRunning.value
      val y = midTask.value
    },
    TaskKey[Unit]("check") := Def.uncached {
      val lines = IO.read(output.value).linesIterator.toList
      assert(lines == List("start", "5 in the middle", "end"))
      ()
    },
    TaskKey[Unit]("check2") := Def.uncached {
      val lines = IO.read(output.value).linesIterator.toList
      assert(lines == List("start", "end", "5 in the middle"))
      ()
    },
  )
