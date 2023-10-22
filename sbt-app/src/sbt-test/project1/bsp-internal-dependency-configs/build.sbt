lazy val a = project.in(file("a")).dependsOn(b % Test)

lazy val b = project.in(file("b")).dependsOn(c)

lazy val c = project.in(file("c"))

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
}
