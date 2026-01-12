// Test for https://github.com/sbt/sbt/issues/8026
// When building sbt plugins with explicit scalaVersion set,
// updateSbtClassifiers should use the correct Scala version for the sbt version.

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "test-sbt-cross-build",

    // Explicitly set scala version - this is what caused the issue in #8026
    // When scalaVersion is explicitly set, updateSbtClassifiers was using the
    // launcher's Scala version instead of the plugin's Scala version.
    // Before the fix, this would fail with:
    //   Error downloading org.scala-sbt:scripted-plugin_2.12:0.13.17
    // because it used the launcher's Scala version (2.12) instead of the
    // plugin's target Scala version derived from sbt binary version.
    scalaVersion := "2.12.21",
  )
