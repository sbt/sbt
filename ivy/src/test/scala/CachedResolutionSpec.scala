package sbt

import org.specs2._

class CachedResolutionSpec extends BaseIvySpecification {
  def is = args(sequential = true) ^ s2"""

  This is a specification to check the cached resolution

  Resolving the same module twice should
    work                                                        $e1

  Resolving the unsolvable module should
    not work                                                    $e2
                                                                """

  def commonsIo13 = ModuleID("commons-io", "commons-io", "1.3", Some("compile"))
  def mavenCayennePlugin302 = ModuleID("org.apache.cayenne.plugins", "maven-cayenne-plugin", "3.0.2", Some("compile"))

  def defaultOptions = EvictionWarningOptions.default

  import ShowLines._

  def e1 = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(commonsIo13), Some("2.10.2"), UpdateOptions().withCachedResolution(true))
    val report = ivyUpdate(m)
    val report2 = ivyUpdate(m)
    println(report)
    println(report.configurations.head.modules.head.artifacts)
    report.configurations.size must_== 3
  }

  def e2 = {
    log.setLevel(Level.Debug)
    val m = module(ModuleID("com.example", "foo", "0.2.0", Some("compile")), Seq(mavenCayennePlugin302), Some("2.10.2"), UpdateOptions().withCachedResolution(true))
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
}
