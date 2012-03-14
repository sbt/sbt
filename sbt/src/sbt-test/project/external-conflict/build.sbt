organization := "org.example"

name := "app"

version := "0.1.17"

publishTo <<= baseDirectory(base => Some(Resolver.file("sample", base / "repo")))

resolvers <++= publishTo(_.toList)

ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "ivy-cache")))