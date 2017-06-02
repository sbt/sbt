scalaVersion := "2.11.7"

scalacOptions in Compile ++= "-sourcepath" :: (baseDirectory.value / "srcpath").toString :: Nil


