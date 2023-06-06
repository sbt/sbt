ThisBuild / scalaVersion := "2.13.11"

val hedgehogVersion = "0.10.0"

libraryDependencies ++= Seq(
  "qa.hedgehog" %% "hedgehog-core" % hedgehogVersion,
  "qa.hedgehog" %% "hedgehog-runner" % hedgehogVersion,
  "qa.hedgehog" %% "hedgehog-sbt" % hedgehogVersion
).map(_ % Test)
