pluginData := Def.uncached {
  assert(globalPluginUpdate.value.allModules.exists(m => m.name == "junit" && m.revision == "4.11"))
  pluginData.value
}
