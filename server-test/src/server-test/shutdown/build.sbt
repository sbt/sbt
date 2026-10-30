@transient
lazy val gatedSlowTask =
  taskKey[Unit]("Runs until the test creates the release file, so a shutdown can queue behind it")

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

// A plain command is not interrupted by a forced shutdown, so a shutdown queued behind it stays
// waiting until the test creates the release file.
commands += Command.command("gatedCommand") { state =>
  val release = state.baseDir / "release"
  val deadline = System.currentTimeMillis + 120000
  while !release.exists do
    if System.currentTimeMillis > deadline then sys.error("gatedCommand was never released")
    Thread.sleep(100)
  state
}
