
lazy val root = (project in file("."))

lazy val a = proj(project in file("a"))
lazy val b = proj(project in file("b"))
lazy val c = proj(project in file("c"))

def proj(p: Project): Project =
  p.settings(
    ivyPaths := (baseDirectory in root, target in root)( (dir, t) => IvyPaths(dir, Some(t / "ivy-cache"))).value,
    resolvers += (appConfiguration { app => // need this to resolve sbt
      val ivyHome = Classpaths.bootIvyHome(app) getOrElse sys.error("Launcher did not provide the Ivy home directory.")
      Resolver.file("real-local",  ivyHome / "local")(Resolver.ivyStylePatterns)
    }).value,
    resolvers += Resolver.typesafeIvyRepo("releases"), // not sure why this isn't included by default
    resolvers += Resolver.mavenLocal
  )
