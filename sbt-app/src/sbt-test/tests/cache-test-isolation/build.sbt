ThisBuild / scalaVersion := "2.12.21"
Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val Other = config("other").extend(Test)

lazy val common = Seq(
  libraryDependencies += "org.scalatest" % "scalatest_2.12" % "3.0.5" % Test,
  Test / unmanagedSourceDirectories := Seq((LocalRootProject / baseDirectory).value / "shared"),
  Test / extraTestDigests := Nil,
  Test / testPersistentWorker := true
)

lazy val a = project
  .configs(Other)
  .settings(common)
  .settings(inConfig(Other)(Defaults.testSettings))
  .settings(
    Other / unmanagedSourceDirectories := (Test / unmanagedSourceDirectories).value,
    Other / extraTestDigests := Nil,
    Other / classDirectory := (Test / classDirectory).value
  )
lazy val b = project.settings(common)
