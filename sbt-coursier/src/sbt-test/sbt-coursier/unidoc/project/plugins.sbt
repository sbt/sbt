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

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.0")
