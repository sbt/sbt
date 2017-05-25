package sbt

import org.specs2._

class EvictionWarningSpec extends BaseIvySpecification {
  def is = s2"""

  This is a specification to check the eviction warnings

  Eviction of non-overridden scala-library whose scalaVersion should
    be detected                                                 $scalaVersionWarn1
    not be detected if it's disabled                            $scalaVersionWarn2
    print out message about the eviction                        $scalaVersionWarn3
    print out message about the eviction with callers           $scalaVersionWarn4

  Non-eviction of overridden scala-library whose scalaVersion should
    not be detected if it's enabled                             $scalaVersionWarn5
    not be detected if it's disabled                            $scalaVersionWarn6

  Including two (suspect) binary incompatible Java libraries to
  direct dependencies should
    be detected as eviction                                     $javaLibWarn1
    not be detected if it's disabled                            $javaLibWarn2
    print out message about the eviction                        $javaLibWarn3
    print out message about the eviction with callers           $javaLibWarn4

  Including two (suspect) binary compatible Java libraries to
  direct dependencies should
    not be detected as eviction                                 $javaLibNoWarn1
    print out message about the eviction                        $javaLibNoWarn2

  Including two (suspect) transitively binary incompatible Java libraries to
  direct dependencies should
    be detected as eviction                                     $javaLibTransitiveWarn2
    print out message about the eviction if it's enabled        $javaLibTransitiveWarn3

  Including two (suspect) binary incompatible Scala libraries to
  direct dependencies should
    be detected as eviction                                     $scalaLibWarn1
    print out message about the eviction                        $scalaLibWarn2

  Including two (suspect) binary compatible Scala libraries to
  direct dependencies should
    not be detected as eviction                                 $scalaLibNoWarn1
    print out message about the eviction                        $scalaLibNoWarn2

  Including two (suspect) transitively binary incompatible Scala libraries to
  direct dependencies should
    be detected as eviction                                     $scalaLibTransitiveWarn2
    print out message about the eviction if it's enabled        $scalaLibTransitiveWarn3
                                                                """

  def akkaActor214 = ModuleID("com.typesafe.akka", "akka-actor", "2.1.4", Some("compile")) cross CrossVersion.binary
  def akkaActor230 = ModuleID("com.typesafe.akka", "akka-actor", "2.3.0", Some("compile")) cross CrossVersion.binary
  def akkaActor234 = ModuleID("com.typesafe.akka", "akka-actor", "2.3.4", Some("compile")) cross CrossVersion.binary
  def scala2102 = ModuleID("org.scala-lang", "scala-library", "2.10.2", Some("compile"))
  def scala2103 = ModuleID("org.scala-lang", "scala-library", "2.10.3", Some("compile"))
  def scala2104 = ModuleID("org.scala-lang", "scala-library", "2.10.4", Some("compile"))
  def commonsIo13 = ModuleID("commons-io", "commons-io", "1.3", Some("compile"))
  def commonsIo14 = ModuleID("commons-io", "commons-io", "1.4", Some("compile"))
  def commonsIo24 = ModuleID("commons-io", "commons-io", "2.4", Some("compile"))
  def bnfparser10 = ModuleID("ca.gobits.bnf", "bnfparser", "1.0", Some("compile")) // uses commons-io 2.4
  def unfilteredUploads080 = ModuleID("net.databinder", "unfiltered-uploads", "0.8.0", Some("compile")) cross CrossVersion.binary // uses commons-io 1.4
  def bananaSesame04 = ModuleID("org.w3", "banana-sesame", "0.4", Some("compile")) cross CrossVersion.binary // uses akka-actor 2.1.4
  def akkaRemote234 = ModuleID("com.typesafe.akka", "akka-remote", "2.3.4", Some("compile")) cross CrossVersion.binary // uses akka-actor 2.3.4

  def defaultOptions = EvictionWarningOptions.default

  import ShowLines._

  def scalaVersionDeps = Seq(scala2102, akkaActor230)

  def scalaVersionWarn1 = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).scalaEvictions must have size (1)
  }

  def scalaVersionWarn2 = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withWarnScalaVersionEviction(false), report, log).scalaEvictions must have size (0)
  }

  def scalaVersionWarn3 = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withShowCallers(false), report, log).lines must_==
      List("Scala version was updated by one of library dependencies:",
        "\t* org.scala-lang:scala-library:2.10.3 is selected over 2.10.2",
        "",
        "To force scalaVersion, add the following:",
        "\tivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }",
        "Run 'evicted' to see detailed eviction warnings")
  }

  def scalaVersionWarn4 = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines must_==
      List("Scala version was updated by one of library dependencies:",
        "\t* org.scala-lang:scala-library:2.10.3 is selected over 2.10.2",
        "\t    +- com.typesafe.akka:akka-actor_2.10:2.3.0            (depends on 2.10.3)",
        "\t    +- com.example:foo:0.1.0                              (depends on 2.10.2)",
        "",
        "To force scalaVersion, add the following:",
        "\tivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }",
        "Run 'evicted' to see detailed eviction warnings")
  }

  def scalaVersionWarn5 = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).scalaEvictions must have size (0)
  }

  def scalaVersionWarn6 = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withWarnScalaVersionEviction(false),
      report, log).scalaEvictions must have size (0)
  }

  def javaLibDirectDeps = Seq(commonsIo14, commonsIo24)

  def javaLibWarn1 = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions must have size (1)
  }

  def javaLibWarn2 = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions
      .withWarnDirectEvictions(false)
      .withWarnTransitiveEvictions(false),
      report, log).reportedEvictions must have size (0)
  }

  def javaLibWarn3 = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines must_==
      List("Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* commons-io:commons-io:2.4 is selected over 1.4",
        "\t    +- com.example:foo:0.1.0                              (depends on 1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings")
  }

  def javaLibWarn4 = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withShowCallers(true), report, log).lines must_==
      List("Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* commons-io:commons-io:2.4 is selected over 1.4",
        "\t    +- com.example:foo:0.1.0                              (depends on 1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings")
  }

  def javaLibNoWarn1 = {
    val deps = Seq(commonsIo14, commonsIo13)
    val m = module(defaultModuleId, deps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions must have size (0)
  }

  def javaLibNoWarn2 = {
    val deps = Seq(commonsIo14, commonsIo13)
    val m = module(defaultModuleId, deps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines must_== Nil
  }

  def javaLibTransitiveDeps = Seq(unfilteredUploads080, bnfparser10)

  def javaLibTransitiveWarn2 = {
    val m = module(defaultModuleId, javaLibTransitiveDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions must have size (1)
  }

  def javaLibTransitiveWarn3 = {
    val m = module(defaultModuleId, javaLibTransitiveDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines must_==
      List("Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* commons-io:commons-io:2.4 is selected over 1.4",
        "\t    +- ca.gobits.bnf:bnfparser:1.0                        (depends on 2.4)",
        "\t    +- net.databinder:unfiltered-uploads_2.10:0.8.0       (depends on 1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings")
  }

  def scalaLibWarn1 = {
    val deps = Seq(scala2104, akkaActor214, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions must have size (1)
  }

  def scalaLibWarn2 = {
    val deps = Seq(scala2104, akkaActor214, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines must_==
      List("Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 is selected over 2.1.4",
        "\t    +- com.example:foo:0.1.0                              (depends on 2.1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings")
  }

  def scalaLibNoWarn1 = {
    val deps = Seq(scala2104, akkaActor230, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions must have size (0)
  }

  def scalaLibNoWarn2 = {
    val deps = Seq(scala2104, akkaActor230, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines must_== Nil
  }

  def scalaLibTransitiveDeps = Seq(scala2104, bananaSesame04, akkaRemote234)

  def scalaLibTransitiveWarn2 = {
    val m = module(defaultModuleId, scalaLibTransitiveDeps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions must have size (1)
  }

  def scalaLibTransitiveWarn3 = {
    val m = module(defaultModuleId, scalaLibTransitiveDeps, Some("2.10.4"))
    val report = ivyUpdate(m)
    val actual = EvictionWarning(m, defaultOptions, report, log).lines
    // println(actual.mkString("\n"))
    actual must_==
      List("Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 is selected over 2.1.4",
        "\t    +- com.typesafe.akka:akka-remote_2.10:2.3.4           (depends on 2.3.4)",
        "\t    +- org.w3:banana-rdf_2.10:0.4                         (depends on 2.1.4)",
        "\t    +- org.w3:banana-sesame_2.10:0.4                      (depends on 2.1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings")
  }
}
