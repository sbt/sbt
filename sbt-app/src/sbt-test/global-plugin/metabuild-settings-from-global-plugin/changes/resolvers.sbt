pluginData := Def.uncached {
  assert(resolvers.value.exists(_.name == "gp-resolver"))
  pluginData.value
}
