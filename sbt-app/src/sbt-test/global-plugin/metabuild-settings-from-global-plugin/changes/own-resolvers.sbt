resolvers := Vector("own-resolver" at (baseDirectory.value / "own-repo").toURI.toString)

pluginData := Def.uncached {
  val names = resolvers.value.map(_.name)
  assert(names.contains("own-resolver"), names)
  assert(names.contains("gp-resolver"), names)
  pluginData.value
}
