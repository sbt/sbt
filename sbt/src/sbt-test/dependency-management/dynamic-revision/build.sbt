lazy val root = (project in file(".")).
  settings(
    libraryDependencies += "org.webjars" %% "webjars-play" % "2.1.0-3",
    resolvers += Resolver.typesafeRepo("releases")
  )
