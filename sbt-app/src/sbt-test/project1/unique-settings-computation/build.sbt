lazy val root = project

val checkComputedOnce = taskKey[Unit]("Check computed once")
checkComputedOnce := {
  val buildValue = (ThisBuild / foo).value
  assert(buildValue == "build 0", "Setting in ThisBuild was computed twice")
  val globalValue = (ThisBuild / foo).value
  assert(globalValue == "global 0", "Setting in Global was computed twice")
}
