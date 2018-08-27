scalaVersion := "2.12.6"

// We can't use "%%" here without breaking the "== bridgeModule" check below
val bridgeModule = "org.scala-sbt" % s"compiler-bridge_2.12" % "1.2.1"

libraryDependencies += bridgeModule % Configurations.ScalaTool

scalaCompilerBridgeSource := "shouldnotbeused" % "dummy" % "dummy"

scalaCompilerBridgeBinaryJar := {
  for {
    toolReport <- update.value.configuration(Configurations.ScalaTool)
    m <- toolReport.modules.find(m => m.module == bridgeModule)
    (_, file) <- m.artifacts.find(art => art._1.`type` == Artifact.DefaultType)
  } yield file
}
