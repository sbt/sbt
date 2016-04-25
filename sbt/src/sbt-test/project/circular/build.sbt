lazy val root = (project in file(".")).
  aggregate(sub).
  settings(
    name := "root"
  )

lazy val sub: Project = project.
  dependsOn(root).
  settingsLazy(
    name := (name in root).value + "sub"
  )

lazy val foo: Project = project.
  aggregateSeq(List(root)).
  dependsOnSeq(List(root)).
  settings(List(
    name := (name in root).value + "foo"
  ): _*)
