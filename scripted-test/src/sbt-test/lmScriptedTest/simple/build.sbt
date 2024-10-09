
sys.props.get("dependency.resolution") match {
  case Some("ivy") =>
    addCommandAlias(
      "setDependencyResolution",
      """set dependencyResolution := sbt.librarymanagement.ivy.IvyDependencyResolution(ivyConfiguration.value)"""
    )
  case Some("coursier") =>
    addCommandAlias(
      "setDependencyResolution",
      """set dependencyResolution := sbt.librarymanagement.coursier.CoursierDependencyResolution(sbt.librarymanagement.coursier.CoursierConfiguration())"""
    )
  case _ => sys.error("""|The system property 'dependency.resolution' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.3"
)
