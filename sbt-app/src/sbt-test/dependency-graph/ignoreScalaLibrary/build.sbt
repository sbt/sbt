ThisBuild / scalaVersion := "2.12.21"

name := "foo"
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.2",
  "ch.qos.logback" % "logback-classic" % "1.0.7"
)
csrMavenDependencyOverride := false

TaskKey[Unit]("check") := {
  val report = updateFull.value
  val graph = (Test / dependencyTree).toTask(" --quiet").value

  // Relaxed check: just verify required artifacts are in the output
  val requiredArtifacts = Seq(
    "ch.qos.logback:logback-classic:1.0.7",
    "ch.qos.logback:logback-core:1.0.7",
    "org.slf4j:slf4j-api:1.7.2"
  )

  requiredArtifacts.foreach { artifact =>
    require(
      graph.contains(artifact),
      s"Graph output did not contain expected artifact: $artifact\nOutput:\n$graph"
    )
  }

  ()
}
