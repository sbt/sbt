val check = taskKey[Unit]("Verify resolvedScalacOptions resolves cache placeholders (#9578).")

scalaVersion := "2.13.16"

addCompilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full))

check := Def.uncached {
  val raw = (Compile / scalacOptions).value
  val resolved = (Compile / resolvedScalacOptions).value

  assert(
    raw.exists(_.contains("${CSR_CACHE}")),
    s"expected a virtualized coursier-cache placeholder in raw scalacOptions, got: $raw"
  )
  assert(
    resolved.forall(!_.contains("${")),
    s"resolvedScalacOptions still contains a cache placeholder: $resolved"
  )

  val pluginArg = resolved
    .find(_.startsWith("-Xplugin:"))
    .getOrElse(sys.error(s"no -Xplugin entry in resolvedScalacOptions: $resolved"))
  val jar = new java.io.File(pluginArg.stripPrefix("-Xplugin:"))
  assert(jar.isAbsolute, s"resolved -Xplugin path is not absolute: $jar")
  assert(jar.exists, s"resolved -Xplugin jar does not exist: $jar")
}
