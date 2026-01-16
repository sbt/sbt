lazy val root = (project in file(".")).
  aggregate(a, b, c, d).
  settings(
    inThisBuild(Seq(
      scalaVersion := "2.11.7",
      trackInternalDependencies := TrackLevel.NoTracking
    ))
  )

lazy val a = project in file("a")

lazy val b = (project in file("b")).dependsOn(a % "*->compile")

lazy val c = (project in file("c")).settings(exportToInternal := TrackLevel.NoTracking)

lazy val d = (project in file("d"))
  .dependsOn(c % "test->test;compile->compile")
  .settings(trackInternalDependencies := TrackLevel.TrackIfMissing)

def getConfigs(key: SettingKey[Seq[(ProjectRef, Set[String])]]):
  Def.Initialize[Map[String, Set[String]]] =
    Def.setting(key.value.map { case (p, c) => p.project -> c }.toMap)
val checkA = taskKey[Unit]("Verify that project a's internal dependencies are as expected")
checkA := {
  val compileDeps = getConfigs(a / Compile / internalDependencyConfigurations).value
  assert(compileDeps == Map("a" -> Set("compile", "optional", "provided", "compile-internal")))
  val testDeps = getConfigs(a / Test / internalDependencyConfigurations).value
  assert(testDeps == Map("a" -> Set("compile", "optional", "provided", "runtime", "test", "test-internal")), testDeps.mkString(", "))
}

val checkB = taskKey[Unit]("Verify that project b's internal dependencies are as expected")
checkB := {
  val compileDeps = getConfigs(b / Compile / internalDependencyConfigurations).value
  assert(compileDeps == Map("b" -> Set("compile",  "optional", "provided", "compile-internal"), "a" -> Set("compile")))
  val testDeps = getConfigs(b / Test / internalDependencyConfigurations).value
  assert(testDeps == Map("b" -> Set("compile", "optional", "provided", "runtime", "test", "test-internal"), "a" -> Set("compile")))
}

val checkC = taskKey[Unit]("Verify that project c's internal dependencies are as expected")
checkC := {
  val compileDeps = getConfigs(c / Compile / internalDependencyConfigurations).value
  assert(compileDeps == Map("c" -> Set("compile",  "optional", "provided", "compile-internal")))
  val testDeps = getConfigs(c / Test / internalDependencyConfigurations).value
  assert(testDeps == Map("c" -> Set("compile", "optional", "provided", "runtime", "test", "test-internal")))
}

val checkD = taskKey[Unit]("Verify that project d's internal dependencies are as expected")
checkD := {
  val compileDeps = getConfigs(d / Compile / internalDependencyConfigurations).value
  assert(compileDeps == Map("d" -> Set("compile",  "optional", "provided", "compile-internal"), "c" -> Set("compile")))
  val testDeps = getConfigs(d / Test / internalDependencyConfigurations).value
  assert(testDeps == Map("d" -> Set("compile", "optional", "provided", "runtime", "test", "test-internal"), "c" -> Set("compile", "test")))
}
