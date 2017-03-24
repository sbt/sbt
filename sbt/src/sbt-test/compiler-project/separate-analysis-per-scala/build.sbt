name := "foo"

scalaVersion := "2.10.6"

crossScalaVersions := List("2.10.6", "2.11.8")

incOptions := incOptions.value.withClassfileManagerType(
  xsbti.Maybe.just(new xsbti.compile.TransactionalManagerType(
    crossTarget.value / "classes.bak",
    (streams in (Compile, compile)).value.log
  ))
)
