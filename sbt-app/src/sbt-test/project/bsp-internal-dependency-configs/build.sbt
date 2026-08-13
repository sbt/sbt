lazy val a = project.in(file("a")).dependsOn(b % Test)

lazy val b = project.in(file("b")).dependsOn(c)

lazy val c = project.in(file("c"))

lazy val d = project.in(file("d")).dependsOn(b % "test -> test")

lazy val e = project.in(file("e")).dependsOn(b % "test ->")

def getConfigs(key: SettingKey[Seq[(ProjectRef, Set[ConfigKey])]]):
  Def.Initialize[Map[String, Set[String]]] =
    Def.setting(key.value.map { case (p, c) => p.project -> c.map(_.name) }.toMap)

TaskKey[Unit]("check") := {
  val testDeps = getConfigs(a / Test / bspInternalDependencyConfigurations).value
  val expected = Map(
    "a" -> Set("compile", "test"),
    "b" -> Set("compile"),
    "c" -> Set("compile")
  )
  assert(testDeps == expected)

  val spacedTestDeps = getConfigs(d / Test / bspInternalDependencyConfigurations).value
  val spacedExpected = Map(
    "d" -> Set("compile", "test"),
    "b" -> Set("compile", "test"),
    "c" -> Set("compile")
  )
  assert(spacedTestDeps == spacedExpected, spacedTestDeps)

  val emptyTargetDeps = getConfigs(e / Test / bspInternalDependencyConfigurations).value
  val emptyTargetExpected = Map("e" -> Set("compile", "test"))
  assert(emptyTargetDeps == emptyTargetExpected, emptyTargetDeps)
}
