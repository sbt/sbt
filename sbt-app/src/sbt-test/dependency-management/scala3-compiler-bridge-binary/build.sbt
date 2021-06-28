ThisBuild / scalaVersion := "3.0.0-M3"

lazy val check = taskKey[Unit]("")

check := {
  val bridge = scalaCompilerBridgeBinaryJar.value
  bridge.getOrElse(sys.error(s"bridge JAR is missing"))
}
