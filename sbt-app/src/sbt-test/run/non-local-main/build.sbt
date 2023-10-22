lazy val main = project.settings(
  organization := "org.scala-sbt.testsuite.example",
  name := "has-main",
  version := "1.0-SNAPSHOT"
)

lazy val user = project.settings(
  fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project"),
  libraryDependencies += (main / projectID).value,
  (Compile / mainClass) := Some("Test")
)

// NOTE - This will NOT work, as mainClass must be scoped by Compile (and optionally task) to function correctly).
lazy val user2 = project.settings(
  fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project"),
  libraryDependencies += (main / projectID).value,
  mainClass := Some("Test")
)

