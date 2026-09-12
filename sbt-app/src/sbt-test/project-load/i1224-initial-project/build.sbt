ThisBuild / autoScalaLibrary := false

ThisBuild / initialProject := Some(i1224SubA)

lazy val checkCurrent = inputKey[Unit]("Asserts which project is currently selected")

@transient
lazy val checkLint = taskKey[Unit]("Asserts initialProject does not trigger lintUnused")

// Defined per project, never at Global: a Global-scoped definition loads fine but
// reports the root project's id whatever is current, which would make every
// assertion below silently vacuous. aggregate := false stops the root's
// aggregation fanning an unscoped invocation out into the subprojects.
lazy val checkSettings = Seq[Setting[?]](
  checkCurrent := {
    val expected = Def.spaceDelimited().parsed.head
    val actual = thisProject.value.id
    assert(actual == expected, s"expected current project $expected, got $actual")
  },
  checkCurrent / aggregate := false,
  checkLint := Def.uncached {
    val st = Keys.state.value
    val includeKeys = (Global / lintIncludeFilter).value
    val excludeKeys = (Global / lintExcludeFilter).value
    val result = sbt.internal.LintUnused.lintUnused(st, includeKeys, excludeKeys)
    val warned = result.filter { case (_, key, _) => key.contains("initialProject") }
    assert(warned.isEmpty, s"initialProject should not be linted, found: ${warned.map(_._2).mkString(", ")}")
  },
  checkLint / aggregate := false,
)

lazy val i1224Root = (project in file("."))
  .aggregate(i1224SubA, i1224SubB)
  .settings(checkSettings)

lazy val i1224SubA = project.settings(checkSettings)

lazy val i1224SubB = project.settings(checkSettings)
