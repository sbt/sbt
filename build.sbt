ScriptedPlugin.scriptedSettings

libraryDependencies ++= Seq(
  "com.github.mdr" %% "ascii-graphs" % "0.0.3",
  "org.specs2"     %% "specs2"       % "2.3.11" % Test
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked"
)

ScalariformSupport.formatSettings
