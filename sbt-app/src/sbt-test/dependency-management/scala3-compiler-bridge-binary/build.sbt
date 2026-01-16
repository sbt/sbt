ThisBuild / scalaVersion := "3.3.4"

lazy val check = taskKey[Unit]("")

check := Def.uncached {
  val bridge = scalaCompilerBridgeBin.value
  if bridge.isEmpty then sys.error(s"bridge JAR is missing")
  else ()
}
