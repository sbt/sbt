@transient
lazy val gatedSlowTask =
  taskKey[Unit]("Runs until the test creates the release file, so a shutdown can queue behind it")

@transient
lazy val quickTask = taskKey[Unit]("Finishes immediately")

gatedSlowTask := Def.uncached {
  val release = baseDirectory.value / "release"
  val deadline = System.currentTimeMillis + 120000
  streams.value.log.info("gatedSlowTask started")
  try
    while !release.exists do
      if System.currentTimeMillis > deadline then sys.error("gatedSlowTask was never released")
      Thread.sleep(100)
  catch
    case e: InterruptedException =>
      IO.touch(baseDirectory.value / "interrupted")
      throw e
}

quickTask := ()

commands += Command.command("gatedSlowCommand") { state =>
  val base = (ThisBuild / baseDirectory).value
  val release = base / "release"
  val deadline = System.currentTimeMillis + 120000
  IO.touch(base / "started")
  while !release.exists do
    if System.currentTimeMillis > deadline then sys.error("gatedSlowCommand was never released")
    Thread.sleep(100)
  state
}
