val defaultSettings = Seq(
  scalaVersion := "2.10.6",
  libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _ )//,
  //incOptions := incOptions.value.withNameHashing(true)
)

lazy val root = (project in file(".")).
  aggregate(macroProvider, macroClient).
  settings(
    defaultSettings
  )

lazy val macroProvider = (project in file("macro-provider")).
  settings(
    defaultSettings
  )

lazy val macroClient = (project in file("macro-client")).
  dependsOn(macroProvider).
  settings(
    defaultSettings
  )
