
lazy val root = (project in file("."))

lazy val a = proj(project in file("a"))
lazy val b = proj(project in file("b"))
lazy val c = proj(project in file("c"))

def proj(p: Project): Project =
  p.settings(
    ivyPaths := IvyPaths((baseDirectory in root).value, Some((target in root).value / "ivy-cache")),
    resolvers += {
      val ivyHome = Classpaths.bootIvyHome(appConfiguration.value) getOrElse sys.error("Launcher did not provide the Ivy home directory.")
      Resolver.file("real-local",  ivyHome / "local")(Resolver.ivyStylePatterns)
    },
    resolvers += Resolver.typesafeIvyRepo("releases"), // not sure why this isn't included by default
    resolvers += Resolver.mavenLocal
  )
