dependencyOverrides := Vector("org.example" % "own" % "1.0")

pluginData := Def.uncached {
  val names = dependencyOverrides.value.map(_.name)
  assert(names.contains("own"), names)
  assert(names.contains("junit"), names)
  pluginData.value
}
