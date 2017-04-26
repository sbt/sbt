package sbt.internal.librarymanagement

import sbt.util.ShowLines
import sbt.librarymanagement._

class CachedResolutionSpec extends BaseIvySpecification {
  import ShowLines._

  "Resolving the same module twice" should "work" in {
    cleanIvyCache()
    val m = module(
      ModuleID("com.example", "foo", "0.1.0").withConfigurations(Some("compile")),
      Vector(commonsIo13),
      Some("2.10.2"),
      UpdateOptions().withCachedResolution(true)
    )
    val report = ivyUpdate(m)
    cleanCachedResolutionCache(m)
    val _ = ivyUpdate(m)
    // first resolution creates the minigraph
    println(report)
    // second resolution reads from the minigraph
    println(report.configurations.head.modules.head.artifacts)
    report.configurations.size shouldBe 3
  }

  "Resolving the unsolvable module should" should "not work" in {
    // log.setLevel(Level.Debug)
    val m = module(
      ModuleID("com.example", "foo", "0.2.0").withConfigurations(Some("compile")),
      Vector(mavenCayennePlugin302),
      Some("2.10.2"),
      UpdateOptions().withCachedResolution(true)
    )
    ivyUpdateEither(m) match {
      case Right(_) => sys.error("this should've failed")
      case Left(uw) =>
        println(uw.lines.mkString("\n"))
    }
    ivyUpdateEither(m) match {
      case Right(_) => sys.error("this should've failed 2")
      case Left(uw) =>
        uw.lines should contain allOf ("\n\tNote: Unresolved dependencies path:",
        "\t\tfoundrylogic.vpp:vpp:2.2.1",
        "\t\t  +- org.apache.cayenne:cayenne-tools:3.0.2",
        "\t\t  +- org.apache.cayenne.plugins:maven-cayenne-plugin:3.0.2",
        "\t\t  +- com.example:foo:0.2.0")
    }
  }

  // https://github.com/sbt/sbt/issues/2046
  // data-avro:1.9.40 depends on avro:1.4.0, which depends on netty:3.2.1.Final.
  // avro:1.4.0 will be evicted by avro:1.7.7.
  // #2046 says that netty:3.2.0.Final is incorrectly evicted by netty:3.2.1.Final
  "Resolving a module with a pseudo-conflict" should "work" in {
    // log.setLevel(Level.Debug)
    cleanIvyCache()
    val m = module(
      ModuleID("com.example", "foo", "0.3.0").withConfigurations(Some("compile")),
      Vector(avro177, dataAvro1940, netty320),
      Some("2.10.2"),
      UpdateOptions().withCachedResolution(true)
    )
    // first resolution creates the minigraph
    val _ = ivyUpdate(m)
    cleanCachedResolutionCache(m)
    // second resolution reads from the minigraph
    val report = ivyUpdate(m)
    val modules: Seq[String] = report.configurations.head.modules map { _.toString }
    assert(modules exists { x: String =>
      x contains """org.jboss.netty:netty:3.2.0.Final"""
    })
    assert(!(modules exists { x: String =>
      x contains """org.jboss.netty:netty:3.2.1.Final"""
    }))
  }

  def commonsIo13 = ModuleID("commons-io", "commons-io", "1.3").withConfigurations(Some("compile"))
  def mavenCayennePlugin302 =
    ModuleID("org.apache.cayenne.plugins", "maven-cayenne-plugin", "3.0.2").withConfigurations(
      Some("compile"))
  def avro177 = ModuleID("org.apache.avro", "avro", "1.7.7").withConfigurations(Some("compile"))
  def dataAvro1940 =
    ModuleID("com.linkedin.pegasus", "data-avro", "1.9.40").withConfigurations(Some("compile"))
  def netty320 =
    ModuleID("org.jboss.netty", "netty", "3.2.0.Final").withConfigurations(Some("compile"))

  def defaultOptions = EvictionWarningOptions.default
}
