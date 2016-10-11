scalaVersion := "2.10.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0.M6-SNAP28"

testOptions in Test += Tests.Argument("-r", "custom.CustomReporter")

parallelExecution in Test := true
