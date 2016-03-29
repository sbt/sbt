lazy val root1 = (project in file(".")).
  aggregate(root2)

lazy val root2 = ProjectRef(uri("external"), "root2")
