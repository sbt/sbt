name := "foo"

scalaVersion := "2.10.4"

crossScalaVersions := List("2.10.4", "2.11.0")

incOptions := incOptions.value.withClassfileManagerType(
  xsbti.Maybe.just(new xsbti.compile.TransactionalManagerType(
    crossTarget.value / "classes.bak",
    (streams in (Compile, compile)).value.log
  ))
)
