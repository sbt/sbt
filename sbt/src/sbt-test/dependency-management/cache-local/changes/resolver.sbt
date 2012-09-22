
publishTo <<= baseDirectory(base => Some(Resolver.file("filesys-publish", base / "repo")) )

resolvers <+= baseDirectory(base => Resolver.file("filesys", base / "repo"))

