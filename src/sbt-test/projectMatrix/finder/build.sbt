lazy val scala213 = "2.13.3"
lazy val scala212 = "2.12.12"
lazy val check = taskKey[Unit]("")


lazy val config12 = ConfigAxis("Config1_2", "config1.2")
lazy val config13 = ConfigAxis("Config1_3", "config1.3")

lazy val root = (project in file("."))
  .aggregate((core.projectRefs ++ custom.projectRefs):_*)

lazy val core = (projectMatrix in file("core"))
  .jvmPlatform(scalaVersions = Seq(scala213, scala212))
  .jsPlatform(scalaVersions = Seq(scala212))

lazy val custom =
  (projectMatrix in file("custom"))
  .customRow(
    scalaVersions = Seq(scala212),
    axisValues = Seq(config13, VirtualAxis.jvm),
    _.settings()
  )

check := {
  val coreResults: Map[Project, Set[VirtualAxis]] = core.findAll().mapValues(_.toSet)
  val customResults: Map[Project, Set[VirtualAxis]] = custom.findAll().mapValues(_.toSet)

  val isJvm = VirtualAxis.jvm
  val isJs = VirtualAxis.js
  val is213 = VirtualAxis.scalaPartialVersion(scala213)
  val is212 = VirtualAxis.scalaPartialVersion(scala212)

  val coreSubProjects = Set(
    core.jvm(scala213), core.jvm(scala212),
    core.js(scala212)
  )

  assert(coreResults.keySet == coreSubProjects)
  assert(coreResults(core.jvm(scala213)) == Set(isJvm, is213))
  assert(coreResults(core.jvm(scala212)) == Set(isJvm, is212))
  assert(coreResults(core.js(scala212)) == Set(isJs, is212))

  assert(customResults.keySet == Set(custom.jvm(scala212)))
  assert(customResults(custom.jvm(scala212)) == Set(isJvm, is212, config13))
}
