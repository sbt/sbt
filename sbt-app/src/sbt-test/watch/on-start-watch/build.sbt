val checkCount = inputKey[Unit]("check that compile has run a specified number of times")
val failingTask = taskKey[Unit]("should always fail")
val resetCount = taskKey[Unit]("reset compile count")

checkCount := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  if (Count.get != expected)
    throw new IllegalStateException(s"Expected $expected compilation runs, got ${Count.get}")
}

resetCount := {
  Count.reset()
}

failingTask := {
  throw new IllegalStateException("failed")
}

watchOnIteration := { (count, project, commands) =>
  val extra = baseDirectory.value / "extra.sbt"
  IO.copyFile(baseDirectory.value / "changes" / "extra.sbt", extra, CopyOptions().withOverwrite(true))
  Watch.defaultStartWatch(count, project, commands).foreach(_.linesIterator.foreach(l => println(s"[info] $l")))
  Watch.Ignore
}

Global / onChangedBuildSource := ReloadOnSourceChanges
