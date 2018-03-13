package coursier

object SbtCompatibility {
  def needsIvyXmlLocal = List(sbt.Keys.deliverLocalConfiguration)
  def needsIvyXml = List(sbt.Keys.deliverConfiguration)

  implicit class ResolverCompationExtraOps(val res: sbt.Resolver.type) {
    def SbtRepositoryRoot = res.SbtPluginRepositoryRoot
  }
}
