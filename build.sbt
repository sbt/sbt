ScriptedPlugin.scriptedSettings

libraryDependencies ++= {
  println(s"Evaluated ${sbtVersion in pluginCrossBuild value}")
  if ((sbtVersion in pluginCrossBuild).value startsWith "0.13")
    Seq("com.github.mdr" %% "ascii-graphs" % "0.0.3")
  else
    Nil
}

libraryDependencies += "org.specs2" %% "specs2-core" % "3.9.1" % "test"

libraryDependencies += Defaults.sbtPluginExtra(
  "com.dwijnand" % "sbt-compat" % "1.0.0+2-ae121c50",
  (sbtBinaryVersion in pluginCrossBuild).value,
  (scalaBinaryVersion in update).value
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

ScalariformSupport.formatSettings

crossSbtVersions := Seq("1.0.1", "0.13.16")

//sbtVersion in pluginCrossBuild := "1.0.0"

/*
Try to prevent silly warnings

libraryDependencies += ("org.scala-sbt" %% "main-settings" % "1.0.1-SNAPSHOT")//.excludeAll(ExclusionRule(organization = "org.scala-sbt"))

libraryDependencies += "org.scala-sbt" %% "command" % "1.0.0"force()
libraryDependencies += "org.scala-sbt" %% "completion" % "1.0.0"force()
libraryDependencies += "org.scala-sbt" %% "task-system" % "1.0.0"force()
libraryDependencies += "org.scala-sbt" %% "core-macros" % "1.0.0" force()

*/