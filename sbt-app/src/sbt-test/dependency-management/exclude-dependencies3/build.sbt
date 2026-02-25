ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ivyPaths := IvyPaths(
  (ThisBuild / baseDirectory).value.toString,
  Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString)
)
resolvers += "test-resolver" at ((ThisBuild / baseDirectory).value / "repo").toURI.toString

organization := "org.example"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.12.20"

@scala.annotation.nowarn
lazy val libWithExcludedDeps = (project in file("lib"))
  .settings(
    name := "lib-with-excluded-deps",
    libraryDependencies += ("org.typelevel" %% "cats-effect" % "3.6.3")
      .excludeAll("org.typelevel" %% "cats-effect-kernel")
      .exclude("org.typelevel", "cats-effect-std_2.12")
      .exclude("org.typelevel" %% "cats-mtl"),
    publishTo := Some(Resolver.file("test-publish", (ThisBuild / baseDirectory).value / "repo")),
  )

lazy val dependsOnLibWithExcludedDeps = (project in file("."))
  .settings(
    name := "depends-on-lib-with-excluded-deps",
    libraryDependencies += "org.example" %% "lib-with-excluded-deps" % "0.1.0-SNAPSHOT",
  )
