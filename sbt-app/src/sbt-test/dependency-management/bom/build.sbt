ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

// BOM (Bill of Materials) consumption: .pomOnly() + version "*" (sbt#4531)
scalaVersion := "2.12.18"

libraryDependencies ++= Seq(
  ("com.fasterxml.jackson" % "jackson-bom" % "2.17.0").pomOnly(),
  "com.fasterxml.jackson.core" % "jackson-core" % "*"
)

TaskKey[Unit]("checkBomResolved") := {
  val report = (Compile / updateFull).value
  val compileConfig = report.configurations.find(_.configuration.name == "compile").getOrElse(
    sys.error("compile configuration not found")
  )
  val jacksonCoreReport = compileConfig.modules.find(_.module.name == "jackson-core").getOrElse(
    sys.error("jackson-core not found in update report")
  )
  val expectedVersion = "2.17.0"
  if (jacksonCoreReport.module.revision != expectedVersion)
    sys.error(s"Expected jackson-core version $expectedVersion from BOM, got ${jacksonCoreReport.module.revision}")
}
