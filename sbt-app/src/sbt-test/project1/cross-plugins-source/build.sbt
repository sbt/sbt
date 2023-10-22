lazy val root = (project in file("."))
  .settings(
    sbtPlugin := true,
    pluginCrossBuild / sbtVersion := "0.13.15",
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
