ScriptedPlugin.scriptedSettings

libraryDependencies += "com.github.mdr" %% "ascii-graphs" % "0.0.3"

libraryDependencies += "org.specs2" %% "specs2" % "2.3.11" % "test"

scalacOptions ++= Seq("-deprecation", "-unchecked")

ScalariformSupport.formatSettings
