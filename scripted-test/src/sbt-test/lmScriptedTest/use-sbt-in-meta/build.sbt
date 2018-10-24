
{
  def writePluginsSbt(str: String) = {
    val pluginsSbt = file(".") / "project" / "plugins.sbt"
    if (!pluginsSbt.exists)
      IO.write(
        pluginsSbt,
        str
      )
  }
  val dr = sys.props.get("dependency.resolution") match {
    case Some("ivy") =>
      """dependencyResolution := sbt.librarymanagement.ivy.IvyDependencyResolution(ivyConfiguration.value)"""
    case Some("coursier") =>
      """dependencyResolution := sbt.librarymanagement.coursier.CoursierDependencyResolution(sbt.librarymanagement.coursier.CoursierConfiguration())"""
    case _ => sys.error("""|The system property 'dependency.resolution' is not defined.
                          |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  }

  writePluginsSbt(dr)
  addCommandAlias(
    "setDependencyResolution",
    s"""set $dr"""
  )
}
