ThisBuild / scalaVersion := "2.12.20"

name := "hello"
enablePlugins(JavaAppPackaging)

lazy val check = taskKey[Unit]("")

check := {
  val cmd = "target/universal/stage/bin/hello"
  val cmd0 =
    if (sys.props("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows"))
      cmd + ".bat"
    else
      cmd
  val b = new ProcessBuilder(cmd0)
  b.inheritIO()
  val p = b.start()
  val retCode = p.waitFor()
  assert(retCode == 0, s"Command $cmd returned code $retCode")
}
