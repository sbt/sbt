lazy val m3 = (project in file("m3"))
  .settings(
    scalaVersion := "3.0.0-M3",
    resolvers += Resolver.JCenterRepository
  )

lazy val rc1 = (project in file("rc1"))
  .settings(
    scalaVersion := "3.0.0-RC1"
  )

