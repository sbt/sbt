pluginData := Def.uncached {
  assert(thisProject.value.autoPlugins.exists(_.label == "ga.DefaultUpdate"))
  assert(globalPluginUpdate.value.configurations.nonEmpty)
  pluginData.value
}
