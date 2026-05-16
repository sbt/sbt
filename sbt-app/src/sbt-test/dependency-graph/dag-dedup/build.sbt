// Regression test for sbt/sbt#6886. Local multi-project diamond: the
// root depends on subA and subB, both of which depend on common. So
// `dependencyTree` for the root must render `common` fully on its
// first occurrence and collapse to `(*)` on the second. Local
// fixture (no external maven dependencies) so this test stays stable
// across Coursier behavior changes and external library churn.
ThisBuild / organization := "org.example"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.13.16"

lazy val common = (project in file("common"))
  .settings(name := "common")

lazy val subA = (project in file("subA"))
  .settings(name := "subA")
  .dependsOn(common)

lazy val subB = (project in file("subB"))
  .settings(name := "subB")
  .dependsOn(common)

lazy val root = (project in file("."))
  .settings(name := "root")
  .dependsOn(subA, subB)
  .aggregate(common, subA, subB)

// Grep-based check, not full diff: Coursier may legitimately reorder
// siblings, and pinning exact line offsets makes the test brittle.
TaskKey[Unit]("check") := {
  val tree = IO.read(file("target/tree.txt"))
  // Tie the assertion to the specific diamond this fixture exercises:
  // `common` must appear on at least one line that also carries the
  // `(*)` marker. A regression where `common` specifically fails to
  // collapse would otherwise slip through as long as scala-library
  // (also reachable through both subA and subB) still collapsed.
  val collapsedCommon = tree.linesIterator.exists(l =>
    l.contains("common") && l.contains("(*)")
  )
  assert(
    collapsedCommon,
    s"expected `common` to appear with a `(*)` marker (diamond apex collapsed):\n$tree"
  )
  // Sanity: cycle marker must NOT appear -- collapse is for duplicate
  // (DAG re-traversal), not cycle (graph back-edge).
  assert(
    !tree.contains("(cycle)"),
    s"unexpected (cycle) marker in dependencyTree output:\n$tree"
  )
}
