ThisBuild / autoScalaLibrary := false

ThisBuild / initialProject := Some(i1224SubA)

lazy val checkCurrent = inputKey[Unit]("Asserts which project is currently selected")
lazy val checkWarning = inputKey[Unit]("Asserts that a warning in the global log mentions the text")

checkCurrent := {
  val expected = Def.spaceDelimited().parsed.head
  val actual = Project.extract(state.value).currentRef.project
  assert(actual == expected, s"expected current project $expected, got $actual")
}

checkWarning := {
  val expected = Def.spaceDelimited().parsed.head
  val warnings = IO
    .readLines(state.value.globalLogging.backing.file)
    .map(sbt.internal.util.EscHelpers.stripColorsAndMoves)
    .filter(_.contains("[warn]"))
  assert(warnings.exists(_.contains(expected)), s"no warning mentions $expected")
}

lazy val i1224Root = (project in file("."))

lazy val i1224SubA = project

lazy val i1224SubB = project
