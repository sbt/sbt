addSbtPlugin("org.example" % "def" % "latest.integration")

resolvers ++= {
	def r(tpe: String) = Resolver.file(s"local-$tpe", baseDirectory.value / ".." / tpe)(using Resolver.ivyStylePatterns)
	r("snapshot") :: r("stable") :: Nil
}
