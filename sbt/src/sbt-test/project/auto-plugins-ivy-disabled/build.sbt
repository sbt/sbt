disablePlugins(plugins.IvyPlugin)

TaskKey[Unit]("check") := {
  val pid = projectID.?.value
  assert(pid.isEmpty)
}