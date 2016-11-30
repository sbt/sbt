val commonSettings = Seq(
  organization := "com.example",
  version := "0.1.0",
  ivyPaths := IvyPaths((baseDirectory in LocalRootProject).value, Some((target in LocalRootProject).value / "ivy-cache"))
)

lazy val app = (project in file("app")).
  settings(commonSettings: _*)

commonSettings
