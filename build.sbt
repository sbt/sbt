ScriptedPlugin.scriptedSettings
ScriptedPlugin.scriptedLaunchOpts += s"-Dproject.version=${version.value}"

libraryDependencies ++= {
  println(s"Evaluated ${sbtVersion in pluginCrossBuild value}")
  if ((sbtVersion in pluginCrossBuild).value startsWith "0.13")
    Seq("com.github.mdr" %% "ascii-graphs" % "0.0.3")
  else
    Nil
}


libraryDependencies += "org.specs2" %% "specs2-core" % "3.9.5" % Test

libraryDependencies += Defaults.sbtPluginExtra(
  "com.dwijnand" % "sbt-compat" % "1.1.0",
  (sbtBinaryVersion in pluginCrossBuild).value,
  (scalaBinaryVersion in update).value
)

crossSbtVersions := Seq("1.0.2", "0.13.16")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked"
)

ScalariformSupport.formatSettings