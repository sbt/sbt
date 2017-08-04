{
  val pluginVersion = sys.props.getOrElse(
    "plugin.version",
    throw new RuntimeException(
      """|The system property 'plugin.version' is not defined.
         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
  )

  addSbtPlugin("io.get-coursier" % "sbt-coursier" % pluginVersion)
}

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.19")
