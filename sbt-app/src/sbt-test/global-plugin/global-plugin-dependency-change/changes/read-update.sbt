pluginData := Def.uncached {
  val _ = globalPluginUpdate.value
  pluginData.value
}
