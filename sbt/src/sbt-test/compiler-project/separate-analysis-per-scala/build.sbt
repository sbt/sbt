name := "foo"

scalaVersion := "2.10.7"

crossScalaVersions := List("2.10.7", "2.11.0")

incOptions := incOptions.value.withNewClassfileManager(
  sbt.inc.ClassfileManager.transactional(
    crossTarget.value / "classes.bak",
    (streams in (Compile, compile)).value.log
  )
)
