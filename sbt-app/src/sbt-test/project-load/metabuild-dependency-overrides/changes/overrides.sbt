dependencyOverrides := Vector.empty

pluginData := Def.uncached {
  assert(dependencyOverrides.value.exists(_.name == "junit"))
  pluginData.value
}
