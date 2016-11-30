organization := "com.example"
version := "0.1.0"
ivyPaths := IvyPaths((baseDirectory in LocalRootProject).value, Some((target in LocalRootProject).value / "ivy-cache"))
