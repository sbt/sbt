lazy val check = taskKey[Unit]("")

check := {
  val ok = baseDirectory.value / "target" / "result-ok"
  val err = baseDirectory.value / "target" / "result-error"
  if (err.exists) sys.error(IO.read(err))
  if (!ok.exists) sys.error("background thread did not finish")
}
