val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
scalaVersion := "2.12.19"
libraryDependencies += scalatest
Test / testOptions += Tests.Argument("-C", "custom.CustomReporter")
