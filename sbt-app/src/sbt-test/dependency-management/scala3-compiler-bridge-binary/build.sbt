ThisBuild / scalaVersion := "3.3.4"

lazy val check = taskKey[Unit]("")

check := {
  val bridge = scalaCompilerBridgeBinaryJar.value
  bridge.getOrElse(sys.error(s"bridge JAR is missing"))
}
