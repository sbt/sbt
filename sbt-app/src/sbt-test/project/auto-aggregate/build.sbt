lazy val root = (project in file("."))
  .autoAggregate
  .settings(
    name := "foo-root",
    publish / skip := true,
  )

lazy val foo = project

lazy val bar = project
  .autoAggregate

lazy val bar1 = (project in file("bar/bar1"))
