pluginData := Def.uncached {
  assert(projectDependencies.value.exists(_.name == "global-plugins"))
  pluginData.value
}
