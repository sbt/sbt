
{
  def writePluginsSbt(str: String) = {
    val pluginsSbt = file(".") / "project" / "plugins.sbt"
  if (!pluginsSbt.exists)
    IO.write(
      pluginsSbt,
      s"""$str
          |addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.8")
          |""".stripMargin
    )
  }
  sys.props.get("dependency.resolution") match {
    case Some("ivy") =>
      writePluginsSbt("""dependencyResolution := sbt.librarymanagement.ivy.IvyDependencyResolution(ivyConfiguration.value)""")
      addCommandAlias(
        "setDependencyResolution",
        """set dependencyResolution := sbt.librarymanagement.ivy.IvyDependencyResolution(ivyConfiguration.value)"""
      )
    case Some("coursier") =>
      writePluginsSbt("""dependencyResolution := sbt.librarymanagement.coursier.CoursierDependencyResolution(Resolver.combineDefaultResolvers(resolvers.value.toVector))""")
      addCommandAlias(
        "setDependencyResolution",
        """set dependencyResolution := sbt.librarymanagement.coursier.CoursierDependencyResolution(Resolver.combineDefaultResolvers(resolvers.value.toVector))"""
      )
    case _ => sys.error("""|The system property 'dependency.resolution' is not defined.
                          |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  }
}
