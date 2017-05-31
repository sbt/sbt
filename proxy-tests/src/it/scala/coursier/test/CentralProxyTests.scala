package coursier.test

object CentralNexus2ProxyTests extends CentralTests {
  override def centralBase = "http://localhost:9081/nexus/content/repositories/central"
}

object CentralNexus3ProxyTests extends CentralTests {
  override def centralBase = "http://localhost:9082/repository/maven-central"
}
