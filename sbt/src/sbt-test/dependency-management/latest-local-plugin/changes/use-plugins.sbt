addSbtPlugin("org.example" % "def" % "latest.integration")

resolvers ++= {
  def r(tpe: String) = Resolver.file(s"local-$tpe", baseDirectory.value / ".." / tpe)(Resolver.ivyStylePatterns)
  r("stable") :: r("snapshot") :: Nil
}
