// Test that dependencyTree works despite eviction errors
// This demonstrates the fix for https://github.com/sbt/sbt/issues/7255

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "com.example"
ThisBuild / version := "1.0.0"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def commonSettings: Seq[Def.Setting[?]] = Seq(
  publishTo := Some(MavenCache("local-maven", (LocalRootProject / baseDirectory).value / "local-repo")),
  resolvers += MavenCache("local-maven", (LocalRootProject / baseDirectory).value / "local-repo"),
)

// Base library with early-semver versioning
lazy val base = project
  .settings(commonSettings)
  .settings(
    name := "base",
    versionScheme := Some("early-semver"),
  )

// Middle library depends on base 1.0.0
lazy val middle = project
  .settings(commonSettings)
  .settings(
    name := "middle",
    libraryDependencies += "com.example" %% "base" % "1.0.0",
  )

// App depends on both middle (which wants base 1.0.0) and base 2.0.0 directly
// This creates a binary incompatible eviction (1.0.0 -> 2.0.0 with early-semver)
lazy val app = project
  .settings(commonSettings)
  .settings(
    name := "app",
    libraryDependencies ++= Seq(
      "com.example" %% "middle" % "1.0.0",
      "com.example" %% "base" % "2.0.0",
    ),
  )

TaskKey[Unit]("checkDependencyTree") := Def.uncached {
  // This task would fail before the fix because eviction errors blocked dependencyTree
  // Now it should succeed and display the tree even with eviction errors
  val tree = (app / Compile / dependencyTree).toTask(" --quiet").value
  assert(tree.contains("base"), s"Tree should contain 'base' but was:\n$tree")
  assert(tree.contains("middle"), s"Tree should contain 'middle' but was:\n$tree")
  streams.value.log.info("dependencyTree succeeded despite eviction conflict!")
}
