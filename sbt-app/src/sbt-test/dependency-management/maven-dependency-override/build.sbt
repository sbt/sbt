ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

scalaVersion := "2.13.18"

libraryDependencies += "com.github.tomakehurst" % "wiremock-jre8" % "2.35.2"
dependencyOverrides += "org.eclipse.jetty" % "jetty-server" % "9.4.58.v20250814"

@transient
lazy val check = inputKey[Unit]("Check resolved Jetty versions")

check := locally:
  val expectedVersion = Def.spaceDelimited("<version>").parsed.head
  val jettyModules = Set("jetty-server", "jetty-http")
  val resolved = update.value.allModules
    .filter(m => m.organization == "org.eclipse.jetty" && jettyModules.contains(m.name))
    .map(m => m.name -> m.revision)
    .toSet
  val expected = Set("jetty-server" -> "9.4.58.v20250814", "jetty-http" -> expectedVersion)
  assert(resolved == expected, s"Expected $expected, got $resolved")
