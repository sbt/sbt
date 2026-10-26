ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

scalaVersion := "2.13.16"

resolvers += "fixture-maven" at (baseDirectory.value / "repo").toURI.toString

libraryDependencies ++= Seq(
  ("com.example" % "test-bom" % "1.0").pomOnly(),
  "commons-io" % "commons-io" % "*" % Provided,
  "commons-codec" % "commons-codec" % "*" % Test,
)

def checkRevision(report: UpdateReport, config: String, name: String, expected: String): Unit =
  val c = report.configurations.find(_.configuration.name == config).getOrElse(
    sys.error(s"$config configuration not found")
  )
  val m = c.modules.find(_.module.name == name).getOrElse(
    sys.error(s"$name not found in $config")
  )
  assert(m.module.revision == expected, s"expected $name $expected in $config, got ${m.module.revision}")

TaskKey[Unit]("check") := {
  val report = update.value
  checkRevision(report, "provided", "commons-io", "2.18.0")
  checkRevision(report, "compile-internal", "commons-io", "2.18.0")
  checkRevision(report, "test", "commons-codec", "1.17.1")
}
