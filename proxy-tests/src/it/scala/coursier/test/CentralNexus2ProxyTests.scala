package coursier.test

object CentralNexus2ProxyTests extends CentralTests {

  val repo = NexusDocker("sonatype/nexus:2.14.4", "nexus/content/repositories/central", 9081)

  override def utestAfterAll(): Unit =
    repo.shutdown()

  override def centralBase = repo.base
}
