resolvers += Resolver.file("ivy-local", file(sys.props("user.home")) / ".ivy2" / "local")(Resolver.ivyStylePatterns)
