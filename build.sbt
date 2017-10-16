ScriptedPlugin.scriptedSettings

libraryDependencies ++= Seq(
  "com.github.mdr" %% "ascii-graphs" % "0.0.7-SNAPSHOT",
  "org.specs2" %% "specs2-core" % "3.9.5" % Test 
)

libraryDependencies += Defaults.sbtPluginExtra(
  "com.dwijnand" % "sbt-compat" % "1.1.0",
  (sbtBinaryVersion in pluginCrossBuild).value,
  (scalaBinaryVersion in update).value
)

crossSbtVersions := List("0.13.16", "1.0.2")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked"
)

ScalariformSupport.formatSettings

addCommandAlias("c1", ";cls;^^ 1.0.2;compile")
addCommandAlias("c0", ";cls;^^ 0.13.16;compile")