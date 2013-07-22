seq(lsSettings :_*)

CrossBuilding.crossSbtVersions := Seq("0.11.1", "0.11.2", "0.11.3", "0.12", "0.13")

crossBuildingSettings

CrossBuilding.scriptedSettings

libraryDependencies += "com.github.mdr" %% "ascii-graphs" % "0.0.3"
