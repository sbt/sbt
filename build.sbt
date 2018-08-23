enablePlugins(ScriptedPlugin)

scriptedLaunchOpts += s"-Dproject.version=${version.value}"

libraryDependencies ++= {
  if ((sbtVersion in pluginCrossBuild).value startsWith "0.13")
    Seq("com.github.mdr" %% "ascii-graphs" % "0.0.3")
  else
    Nil
}


libraryDependencies += "org.specs2" %% "specs2-core" % "3.10.0" % Test

libraryDependencies += Defaults.sbtPluginExtra(
  "com.dwijnand" % "sbt-compat" % "1.2.6",
  (sbtBinaryVersion in pluginCrossBuild).value,
  (scalaBinaryVersion in update).value
)

crossSbtVersions := Seq("1.2.1", "0.13.16")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked"
)

ScalariformSupport.formatSettings