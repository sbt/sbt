ThisBuild / autoScalaLibrary := false

// initialProject is never set here. Each case copies a one-line ip.sbt into the
// build root, which BuildPaths globs alongside this file, so the cases differ by
// exactly one setting and this file never has to be duplicated into changes/.

lazy val checkCurrent = inputKey[Unit]("Asserts which project is currently selected")

lazy val checkSettings = Seq[Setting[?]](
  checkCurrent := {
    val expected = Def.spaceDelimited().parsed.head
    val actual = thisProject.value.id
    assert(actual == expected, s"expected current project $expected, got $actual")
  },
  checkCurrent / aggregate := false,
)

lazy val i1224Root = (project in file("."))
  .aggregate(i1224SubA)
  .settings(checkSettings)

lazy val i1224SubA = project.settings(checkSettings)
