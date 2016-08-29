TaskKey[Unit]("check") := {
  val report = update.value
  val compatJetty = report.allModules.filter(mod => mod.name == "atmosphere-compat-jetty")
  assert(compatJetty.isEmpty, "Expected dependencies to be excluded: " + compatJetty.mkString(", "))
}
