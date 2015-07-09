package sbt

import org.specs2._

class CachedResolutionSpec extends BaseIvySpecification {
  def is = args(sequential = true) ^ s2"""

  This is a specification to check the cached resolution

  Resolving the same module twice should
    work                                                        $e1

  Resolving the unsolvable module should
    not work                                                    $e2

  Resolving a module with a pseudo-conflict should
    work                                                        $e3
                                                                """

  def commonsIo13 = ModuleID("commons-io", "commons-io", "1.3", Some("compile"))
  def mavenCayennePlugin302 = ModuleID("org.apache.cayenne.plugins", "maven-cayenne-plugin", "3.0.2", Some("compile"))
  def avro177 = ModuleID("org.apache.avro", "avro", "1.7.7", Some("compile"))
  def dataAvro1940 = ModuleID("com.linkedin.pegasus", "data-avro", "1.9.40", Some("compile"))
  def netty320 = ModuleID("org.jboss.netty", "netty", "3.2.0.Final", Some("compile"))

  def defaultOptions = EvictionWarningOptions.default

  import ShowLines._

  def e1 = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")),
      Seq(commonsIo13), Some("2.10.2"), UpdateOptions().withCachedResolution(true))
    val report = ivyUpdate(m)
    cleanCachedResolutionCache(m)
    val report2 = ivyUpdate(m)
    // first resolution creates the minigraph
    println(report)
    // second resolution reads from the minigraph
    println(report.configurations.head.modules.head.artifacts)
    report.configurations.size must_== 3
  }

  def e2 = {
    // log.setLevel(Level.Debug)
    val m = module(ModuleID("com.example", "foo", "0.2.0", Some("compile")),
      Seq(mavenCayennePlugin302), Some("2.10.2"), UpdateOptions().withCachedResolution(true))
    ivyUpdateEither(m) match {
      case Right(_) => sys.error("this should've failed")
      case Left(uw) =>
        println(uw.lines.mkString("\n"))
    }
    ivyUpdateEither(m) match {
      case Right(_) => sys.error("this should've failed 2")
      case Left(uw) =>
        uw.lines must contain(allOf("\n\tNote: Unresolved dependencies path:",
          "\t\tfoundrylogic.vpp:vpp:2.2.1",
          "\t\t  +- org.apache.cayenne:cayenne-tools:3.0.2",
          "\t\t  +- org.apache.cayenne.plugins:maven-cayenne-plugin:3.0.2",
          "\t\t  +- com.example:foo:0.2.0"))
    }
  }

  // https://github.com/sbt/sbt/issues/2046
  // data-avro:1.9.40 depends on avro:1.4.0, which depends on netty:3.2.1.Final.
  // avro:1.4.0 will be evicted by avro:1.7.7.
  // #2046 says that netty:3.2.0.Final is incorrectly evicted by netty:3.2.1.Final
  def e3 = {
    log.setLevel(Level.Debug)
    val m = module(ModuleID("com.example", "foo", "0.3.0", Some("compile")),
      Seq(avro177, dataAvro1940, netty320),
      Some("2.10.2"), UpdateOptions().withCachedResolution(true))
    // first resolution creates the minigraph
    val report0 = ivyUpdate(m)
    cleanCachedResolutionCache(m)
    // second resolution reads from the minigraph
    val report = ivyUpdate(m)
    val modules = report.configurations.head.modules
    modules must containMatch("""org\.jboss\.netty:netty:3\.2\.0.Final""")
  }
}
