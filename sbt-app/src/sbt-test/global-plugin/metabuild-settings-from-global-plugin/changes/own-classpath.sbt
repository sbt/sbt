Compile / internalDependencyClasspath := Def.uncached {
  val own = fileConverter.value.toVirtualFile(baseDirectory.value.toPath.resolve("own-classes"))
  (Compile / internalDependencyClasspath).value :+ Attributed.blank(own)
}

pluginData := Def.uncached {
  val ids = (Compile / internalDependencyClasspath).value.map(_.data.id)
  assert(ids.exists(_.contains("own-classes")), ids)
  assert(ids.exists(_.contains("global-plugins")), ids)
  pluginData.value
}
