
publishTo <<= baseDirectory(base => Some(Resolver.file("filesys-publish", base / "repo")) )

resolvers <+= baseDirectory(base => "filesys" at (base / "repo").toURI.toString)

