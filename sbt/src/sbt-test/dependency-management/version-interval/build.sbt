
libraryDependencies += "org.json4s" %% "json4s-native" % "[3.3.0,3.5.0)"


lazy val actualVersionCheck = taskKey[Unit]("")

actualVersionCheck := {

  val log = streams.value.log

  val configReport = update.value
    .configuration(Compile)
    .getOrElse {
      sys.error("compile configuration not found in update report")
    }

  val modules = configReport
    .modules
    .map(_.module)

  assert(modules.nonEmpty)
  assert(modules.exists(_.name.startsWith("json4s-native")))

  val wrongModules = modules.filter { m =>
    val v = m.revision
    v.contains("[") || v.contains("]") || v.contains("(") || v.contains(")")
  }

  if (wrongModules.nonEmpty) {
    log.error("Found unexpected intervals in revisions")
    for (m <- wrongModules)
      log.error(s"  ${m.organization}:${m.name}:${m.revision}")
    sys.error("Found intervals in revisions")
  }
}
