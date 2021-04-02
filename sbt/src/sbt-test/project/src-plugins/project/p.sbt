lazy val root = (project in file(".")).
  dependsOn(RootProject(file("../plugin")))
