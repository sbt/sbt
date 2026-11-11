lazy val check = taskKey[Unit]("")

check := Def.uncached {
  val ok = baseDirectory.value / "target" / "result-ok"
  val err = baseDirectory.value / "target" / "result-error"
  val deadline = System.currentTimeMillis + 60000
  while (!ok.exists && !err.exists && System.currentTimeMillis < deadline) Thread.sleep(100)
  if (err.exists) sys.error(IO.read(err))
  if (!ok.exists) sys.error("background thread did not finish")
}
