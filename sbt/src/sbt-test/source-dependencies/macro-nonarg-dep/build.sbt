val defaultSettings = Seq(
  scalaVersion := "2.11.8",
  libraryDependencies += scalaVersion("org.scala-lang" % "scala-reflect" % _ ).value //,
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
