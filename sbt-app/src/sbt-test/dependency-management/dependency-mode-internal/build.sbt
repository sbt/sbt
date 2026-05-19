// Regression test for sbt/sbt#9009. dependencyMode := Direct / PlusOne
// must apply to *internal* project dependencies as well, walking the
// project graph (UpdateReport only contains external/LM modules).
//
// Fixture: core <- libA <- libB <- libC (each depends on the previous).
//
//   Direct  on libA -- must include core
//   Direct  on libB -- must *not* include core (core is transitive)
//   PlusOne on libB -- must include core (one hop through libA)
//   PlusOne on libC -- must include libA (one hop through libB)
//                      must *not* include core (two hops away)

ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "org.example"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val core = (project in file("core")).settings(name := "core")

lazy val libA = (project in file("libA"))
  .settings(name := "libA")
  .dependsOn(core)

lazy val libB = (project in file("libB"))
  .settings(name := "libB")
  .dependsOn(libA)

lazy val libC = (project in file("libC"))
  .settings(name := "libC")
  .dependsOn(libB)

def filteredIds(cp: Seq[Attributed[xsbti.HashedVirtualFileRef]]): Seq[String] =
  cp.map(_.data.id)

// Match against the lowercased jar filename (sbt lowercases project
// names when building artifact filenames, e.g. `liba_3-...jar`).
def assertIn(needle: String, haystack: Seq[String], label: String): Unit =
  val n = needle.toLowerCase
  assert(
    haystack.exists(_.toLowerCase.contains(n)),
    s"$label: expected `$needle` to appear in $haystack"
  )

def assertNotIn(needle: String, haystack: Seq[String], label: String): Unit =
  val n = needle.toLowerCase
  assert(
    !haystack.exists(_.toLowerCase.contains(n)),
    s"$label: expected `$needle` NOT to appear in $haystack"
  )

lazy val checkDirectLibA = taskKey[Unit]("Direct mode on libA includes core")
lazy val checkDirectLibB = taskKey[Unit]("Direct mode on libB excludes core (transitive)")
lazy val checkPlusOneLibB = taskKey[Unit]("PlusOne mode on libB includes core (one hop)")
lazy val checkPlusOneLibC =
  taskKey[Unit]("PlusOne mode on libC includes libA (one hop) but not core (two hops)")

libA / checkDirectLibA := {
  val cp = filteredIds((libA / Compile / filteredDependencyClasspath).value)
  assertIn("core", cp, "libA/Direct")
}

libB / checkDirectLibB := {
  val cp = filteredIds((libB / Compile / filteredDependencyClasspath).value)
  assertIn("libA", cp, "libB/Direct")
  assertNotIn("core", cp, "libB/Direct")
}

libB / checkPlusOneLibB := {
  val cp = filteredIds((libB / Compile / filteredDependencyClasspath).value)
  assertIn("libA", cp, "libB/PlusOne")
  assertIn("core", cp, "libB/PlusOne")
}

libC / checkPlusOneLibC := {
  val cp = filteredIds((libC / Compile / filteredDependencyClasspath).value)
  assertIn("libB", cp, "libC/PlusOne")
  assertIn("libA", cp, "libC/PlusOne")
  assertNotIn("core", cp, "libC/PlusOne")
}
