lazy val root = project

val checkComputedOnce = taskKey[Unit]("Check computed once")
checkComputedOnce := {
  val buildValue = (foo in ThisBuild).value
  assert(buildValue == "build 0", "Setting in ThisBuild was computed twice")
  val globalValue = (foo in Global).value
  assert(globalValue == "global 0", "Setting in Global was computed twice")
}
