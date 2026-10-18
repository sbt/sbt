// Check the meta-build's update report and classpath while sbt loads the build.
pluginData := Def.uncached {
  val data = pluginData.value
  val files = data.report.toVector.flatMap(_.configurations).flatMap(_.modules).flatMap(_.artifacts)
    .map(_._2)
  assert(files.exists(_.getName.startsWith("global-plugins")), s"no global plugin jar: $files")
  val missing = files.filterNot(_.exists).distinct
  assert(missing.isEmpty, s"missing in update: $missing")
  val cp = data.dependencyClasspath.map(a => data.converter.toPath(a.data).toFile)
  val missingCp = cp.filterNot(_.exists).distinct
  assert(missingCp.isEmpty, s"missing in classpath: $missingCp")
  data
}
