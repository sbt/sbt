import sbt.internal.inc.ScalaInstance

scalaInstance := ScalaInstance(scalaVersion.value, appConfiguration.value.provider.scalaProvider)

scalaVersion := "invalid"
