// Test that dependencyTree works despite eviction errors
// This demonstrates the fix for https://github.com/sbt/sbt/issues/7255

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

// Create a binary incompatible eviction using real Maven dependencies:
// - scalacheck 1.14.3 depends on test-interface 1.0
// - scalacheck 1.15.4 depends on test-interface 1.0
// - We force an eviction by depending on incompatible versions
// Or simpler: use libraries with known eviction issues

// cats-effect 2.x and 3.x are binary incompatible (early-semver)
// fs2 2.5.x depends on cats-effect 2.x
// We also add cats-effect 3.x directly to force eviction
libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "2.5.11",        // depends on cats-effect 2.x
  "org.typelevel" %% "cats-effect" % "3.3.0" // cats-effect 3.x (incompatible)
)
