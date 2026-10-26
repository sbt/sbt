globalPluginUpdate := Def.uncached(
  UpdateReport(file("own-update"), Vector.empty, UpdateStats(0, 0, 0, false), Map.empty)
)

pluginData := Def.uncached {
  assert(globalPluginUpdate.value.configurations.isEmpty)
  pluginData.value
}
