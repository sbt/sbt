seq(lsSettings :_*)

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.11.1", "0.11.2", "0.11.3", "0.12", "0.13")

CrossBuilding.scriptedSettings

libraryDependencies += "com.github.mdr" %% "ascii-graphs" % "0.0.3"

libraryDependencies <++= scalaVersion { version =>
  if (version startsWith "2.1") Seq("org.scala-lang" % "scala-reflect" % version % "provided")
  else Nil
}

libraryDependencies <+= scalaVersion { version =>
  if (version startsWith "2.9") "org.specs2" % "specs2_2.9.3" % "1.12.4.1" % "test"
  else "org.specs2" %% "specs2" % "2.1.1" % "test"
}

scalacOptions ++= Seq("-deprecation", "-unchecked")
