pluginData := Def.uncached {
  val converter = fileConverter.value
  val cp = (Compile / internalDependencyClasspath).value ++
    (Runtime / internalDependencyClasspath).value
  val data = pluginData.value
  val gp = cp.filter(_.data.id.contains("global-plugins"))
  assert(gp.nonEmpty)
  val ids = (gp ++ data.classpath.filter(_.data.id.contains("global-plugins"))).map(_.data.id)
  assert(ids.forall(id => converter.toPath(xsbti.VirtualFileRef.of(id)).toFile.exists))
  data
}
