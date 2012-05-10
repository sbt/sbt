TaskKey[Unit]("check") <<= update map { report =>
	val compatJetty = report.allModules.filter(mod => mod.name == "atmosphere-compat-jetty")
	assert(compatJetty.isEmpty, "Expected dependencies to be excluded: " + compatJetty.mkString(", "))
}
