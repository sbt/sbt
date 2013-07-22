seq(lsSettings :_*)

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.11.1", "0.11.2", "0.11.3", "0.12", "0.13")

CrossBuilding.scriptedSettings

libraryDependencies += "com.github.mdr" %% "ascii-graphs" % "0.0.3"

libraryDependencies <++= scalaVersion { version =>
  if (version startsWith "2.1") Seq("org.scala-lang" % "scala-reflect" % version % "provided")
  else Nil
}

scalacOptions ++= Seq("-deprecation", "-unchecked")
