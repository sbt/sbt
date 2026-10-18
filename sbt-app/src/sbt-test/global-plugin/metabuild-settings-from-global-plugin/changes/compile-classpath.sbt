pluginData := Def.uncached {
  assert((Compile / internalDependencyClasspath).value.exists(_.data.id.contains("global-plugins")))
  pluginData.value
}
