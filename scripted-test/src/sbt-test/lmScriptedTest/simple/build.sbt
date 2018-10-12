
addCommandAlias("setDependencyResolution", sys.props.get("dependency.resolution") match {
  case Some(x) => x
  case _ => sys.error("""|The system property 'dependency.resolution' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
})

libraryDependencies += "com.typesafe" % "config" % "1.3.2"
