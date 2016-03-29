lazy val root = (project in file(".")).
  aggregate(LocalProject("sub"))

lazy val sub = project.
  dependsOn(root)
