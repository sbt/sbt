addSbtPlugin {

  val name = sys.props.getOrElse(
    "plugin.name",
    sys.error("plugin.name Java property not set")
  )
  val version = sys.props.getOrElse(
    "plugin.version",
    sys.error("plugin.version Java property not set")
  )

  "io.get-coursier" % name % version
}