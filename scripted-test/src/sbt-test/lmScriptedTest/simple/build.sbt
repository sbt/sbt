
sys.props.get("dependency.resolution") match {
  case Some("ivy") =>
    dependencyResolution := sbt.librarymanagement.ivy.IvyDependencyResolution(ivyConfiguration.value)
  case Some("coursier") =>
    dependencyResolution := sbt.librarymanagement.coursier.CoursierDependencyResolution(resolvers.value)
  case _ => sys.error("""|The system property 'dependency.resolution' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.2",
  // "org.scala-lang" % "scala-compiler" % "2.12.7" % Compile
)
