// sbt-ivy provides the Ivy publish backend exercised by the `ivyfull` project.
libraryDependencies += Defaults.sbtPluginExtra(
  "org.scala-sbt" % "sbt-ivy" % sbtVersion.value,
  sbtVersion.value,
  scalaVersion.value,
)
