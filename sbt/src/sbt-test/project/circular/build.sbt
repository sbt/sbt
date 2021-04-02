lazy val root = (project in file(".")).
  aggregate(sub).
  settings(
    name := "root"
  )

lazy val sub: Project = project.
  dependsOn(LocalProject("root")).
  settings(
    name := (name in LocalProject("root")).value + "sub"
  )

lazy val foo: Project = project.
  aggregate(LocalProject("root")).
  dependsOn(LocalProject("root")).
  settings(List(
    name := (name in LocalProject("root")).value + "foo"
  ): _*)
