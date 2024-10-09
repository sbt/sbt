scalaVersion.in(ThisBuild) := "0.16.0-RC3"

organization.in(ThisBuild) := "ch.epfl.lamp"
version.in(ThisBuild) := "0.16.0-bin-SNAPSHOT"

// same projects (same module / versions) as the dotty-build
// these shouldn't make the internal dependency resolutions of sbt-dotty crash
lazy val `dotty-compiler` = project
lazy val `dotty-doc` = project
lazy val `dotty-interfaces` = project
lazy val `dotty-library` = project
lazy val `dotty-sbt-bridge` = project
lazy val `dotty` = project

lazy val foo = project
  .in(file("."))
  .dependsOn(
    `dotty-compiler`,
    `dotty-doc`,
    `dotty-interfaces`,
    `dotty-library`,
    `dotty-sbt-bridge`,
    `dotty`
  )
