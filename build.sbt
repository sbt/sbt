ScriptedPlugin.scriptedSettings

libraryDependencies ++= {
  if (sbtVersion.value startsWith "0.13")
    Seq("com.github.mdr" %% "ascii-graphs" % "0.0.3")
  else
    Nil
}

libraryDependencies += "org.specs2" %% "specs2-core" % "3.9.1" % "test"

scalacOptions ++= Seq("-deprecation", "-unchecked")

ScalariformSupport.formatSettings
