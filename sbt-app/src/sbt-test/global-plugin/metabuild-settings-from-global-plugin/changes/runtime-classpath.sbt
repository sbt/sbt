pluginData := Def.uncached {
  assert((Runtime / internalDependencyClasspath).value.exists(_.data.id.contains("global-plugins")))
  pluginData.value
}
