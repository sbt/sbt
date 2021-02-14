ThisBuild / scalaVersion := "3.0.0-M4-bin-20210212-0273336-NIGHTLY"
// ThisBuild / scalaVersion := "3.0.0-M3",

lazy val root = (project in file("."))
  .settings(
    resolvers += Resolver.JCenterRepository
  )
