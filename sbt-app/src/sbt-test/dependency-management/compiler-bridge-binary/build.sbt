ThisBuild / scalaVersion := "2.12.20"

lazy val check = taskKey[Unit]("")

// We can't use "%%" here without breaking the "== bridgeModule" check below
val bridgeModule = "org.scala-sbt" % "compiler-bridge_2.12" % "1.3.0"

libraryDependencies += bridgeModule % Configurations.ScalaTool

scalaCompilerBridgeSource := "shouldnotbeused" % "dummy" % "dummy"

scalaCompilerBridgeBinaryJar := {
  for {
    toolReport <- update.value.configuration(Configurations.ScalaTool)
    m <- toolReport.modules.find(m => m.module.name == bridgeModule.name)
    (_, file) <- m.artifacts.find(art => art._1.`type` == Artifact.DefaultType)
  } yield file
}

check := {
  val toolReport = update.value.configuration(Configurations.ScalaTool).get
  val m = toolReport.modules.find(m => m.module.name == bridgeModule.name)
  val bridge = scalaCompilerBridgeBinaryJar.value
  bridge.getOrElse(sys.error(s"bridge JAR is missing: $toolReport"))
}
