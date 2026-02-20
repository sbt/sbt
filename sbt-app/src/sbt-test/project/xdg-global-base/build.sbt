// Scripted test for #3681: default global base (XDG / sbt.global.base) is used correctly.
lazy val checkGlobalBase = taskKey[Unit]("Verifies global base is absolute and non-empty")

lazy val root = (project in file(".")).settings(
  checkGlobalBase := {
    val g = BuildPaths.getGlobalBase(state.value)
    assert(g.isAbsolute, s"expected absolute path: $g")
    assert(g.getAbsolutePath.nonEmpty, "global base path must be non-empty")
  }
)
