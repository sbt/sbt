
publishTo := baseDirectory(base => Some(Resolver.file("filesys-publish", base / "repo")) ).value

resolvers += baseDirectory(base => "filesys" at (base / "repo").toURI.toString).value

