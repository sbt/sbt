lazy val other = RootProject(file("other"))
lazy val root = (project in file(".")).dependsOn(other)
