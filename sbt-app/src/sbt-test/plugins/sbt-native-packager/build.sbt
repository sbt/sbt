ThisBuild / scalaVersion := "3.9.0"

name := "hello"
enablePlugins(JavaAppPackaging)

// https://github.com/sbt/sbt/issues/9676
enablePlugins(UniversalDeployPlugin)

@transient
lazy val check = taskKey[Unit]("")

check := {
  val cmd = "target/out/jvm/scala-3.9.0/hello/universal/stage/bin/hello"
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
