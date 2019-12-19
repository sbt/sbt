TaskKey[Unit]("checkSbtVersionWarning") := {
	val state = Keys.state.value
	val logging = state.globalLogging
	val currVersion = state.configuration.provider.id.version()
	val contents = IO.read(logging.backing.file)
	assert(contents.contains(s"""sbt version mismatch, using: $currVersion, in build.properties: "1.1.1"."""))
	()
}