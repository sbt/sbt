package sbt.internal.librarymanagement

import sbt.librarymanagement._
import sbt.internal.librarymanagement.cross.CrossVersionUtil
import sbt.librarymanagement.syntax._

object EvictionWarningSpec extends BaseIvySpecification {
  // This is a specification to check the eviction warnings

  import TestShowLines.*

  def scalaVersionDeps = Vector(scala2102, akkaActor230)

  test("Eviction of non-overridden scala-library whose scalaVersion should be detected") {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).scalaEvictions.size == 1)
  }

  test("it should not be detected if it's disabled") {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(
        m,
        fullOptions.withWarnScalaVersionEviction(false),
        report
      ).scalaEvictions.size == 0
    )
  }

  test("it should print out message about the eviction") {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, fullOptions.withShowCallers(false), report).lines ==
        List(
          "Scala version was updated by one of library dependencies:",
          "\t* org.scala-lang:scala-library:2.10.3 is selected over 2.10.2",
          "",
          "To force scalaVersion, add the following:",
          "\tscalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true)))"
        )
    )
  }

  test("it should print out message about the eviction with callers") {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, fullOptions, report).lines ==
        List(
          "Scala version was updated by one of library dependencies:",
          "\t* org.scala-lang:scala-library:2.10.3 is selected over 2.10.2",
          "\t    +- com.typesafe.akka:akka-actor_2.10:2.3.0            (depends on 2.10.3)",
          "\t    +- com.example:foo:0.1.0                              (depends on 2.10.2)",
          "",
          "To force scalaVersion, add the following:",
          "\tscalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true)))"
        )
    )
  }

  test("it should print out summary about the eviction if warn eviction summary enabled") {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, EvictionWarningOptions.summary, report).lines ==
        List(
          "There may be incompatibilities among your library dependencies; run 'evicted' to see detailed eviction warnings."
        )
    )
  }

  test(
    """Non-eviction of overridden scala-library whose scalaVersion  should "not be detected if it's enabled""""
  ) {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).scalaEvictions.size == 0)
  }

  test("it should not be detected if it's disabled") {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(
        m,
        fullOptions.withWarnScalaVersionEviction(false),
        report
      ).scalaEvictions.size == 0
    )
  }

  test(
    """Including two (suspect) binary incompatible Java libraries to direct dependencies should be detected as eviction"""
  ) {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).reportedEvictions.size == 1)
  }

  test("it should not be detected if it's disabled") {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(
        m,
        fullOptions
          .withWarnDirectEvictions(false)
          .withWarnTransitiveEvictions(false),
        report
      ).reportedEvictions.size == 0
    )
  }

  test("it should print out message about the eviction") {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, fullOptions, report).lines ==
        List(
          "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* commons-io:commons-io:2.4 is selected over 1.4",
          "\t    +- com.example:foo:0.1.0                              (depends on 1.4)",
          ""
        )
    )
  }

  test("it should print out message about the eviction with callers") {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, fullOptions.withShowCallers(true), report).lines ==
        List(
          "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* commons-io:commons-io:2.4 is selected over 1.4",
          "\t    +- com.example:foo:0.1.0                              (depends on 1.4)",
          ""
        )
    )
  }

  test("it should print out summary about the eviction if warn eviction summary enabled") {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, EvictionWarningOptions.summary, report).lines ==
        List(
          "There may be incompatibilities among your library dependencies; run 'evicted' to see detailed eviction warnings."
        )
    )
  }

  test(
    """Including two (suspect) binary compatible Java libraries to direct dependencies should not be detected as eviction"""
  ) {
    val deps = Vector(commonsIo14, commonsIo13)
    val m = module(defaultModuleId, deps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).reportedEvictions.size == 0)
  }

  test("it should not print out message about the eviction") {
    val deps = Vector(commonsIo14, commonsIo13)
    val m = module(defaultModuleId, deps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).lines == Nil)
  }

  test(
    """Including two (suspect) transitively binary incompatible Java libraries to direct dependencies should be detected as eviction"""
  ) {
    val m = module(defaultModuleId, javaLibTransitiveDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).reportedEvictions.size == 1)
  }

  test(
    """Including two (suspect) binary incompatible Scala libraries to direct dependencies should be detected as eviction"""
  ) {
    val deps = Vector(scala2104, akkaActor214, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).reportedEvictions.size == 1)
  }

  test("it should print out message about the eviction") {
    val deps = Vector(scala2104, akkaActor214, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, fullOptions, report).lines ==
        List(
          "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 is selected over 2.1.4",
          "\t    +- com.example:foo:0.1.0                              (depends on 2.1.4)",
          ""
        )
    )
  }

  test("it should print out summary about the eviction if warn eviction summary enabled") {
    val deps = Vector(scala2104, akkaActor214, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, EvictionWarningOptions.summary, report).lines ==
        List(
          "There may be incompatibilities among your library dependencies; run 'evicted' to see detailed eviction warnings."
        )
    )
  }

  test(
    """Including two (suspect) binary compatible Scala libraries to direct dependencies should not be detected as eviction"""
  ) {
    val deps = Vector(scala2104, akkaActor230, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).reportedEvictions.size == 0)
  }

  test("it should not print out message about the eviction") {
    val deps = Vector(scala2104, akkaActor230, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).lines == Nil)
  }

  test("it should not print out summary about the eviction even if warn eviction summary enabled") {
    val deps = Vector(scala2104, akkaActor230, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, EvictionWarningOptions.summary, report).lines == Nil)
  }

  test(
    """Including two (suspect) transitively binary incompatible Scala libraries to direct dependencies should be detected as eviction"""
  ) {
    val m = module(defaultModuleId, scalaLibTransitiveDeps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(EvictionWarning(m, fullOptions, report).reportedEvictions.size == 1)
  }

  test("it should print out message about the eviction if it's enabled") {
    val m = module(defaultModuleId, scalaLibTransitiveDeps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, fullOptions, report).lines ==
        List(
          "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 is selected over 2.1.4",
          "\t    +- com.typesafe.akka:akka-remote_2.10:2.3.4           (depends on 2.3.4)",
          "\t    +- org.w3:banana-rdf_2.10:0.4                         (depends on 2.1.4)",
          "\t    +- org.w3:banana-sesame_2.10:0.4                      (depends on 2.1.4)",
          ""
        )
    )
  }

  test("it should print out summary about the eviction if warn eviction summary enabled") {
    val m = module(defaultModuleId, scalaLibTransitiveDeps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(
      EvictionWarning(m, EvictionWarningOptions.summary, report).lines ==
        List(
          "There may be incompatibilities among your library dependencies; run 'evicted' to see detailed eviction warnings."
        )
    )
  }

  test("Comparing sbt 0.x should use Second Segment Variation semantics") {
    val m1 = "org.scala-sbt" % "util-logging" % "0.13.16"
    val m2 = "org.scala-sbt" % "util-logging" % "0.13.1"
    assert(
      !EvictionWarningOptions.defaultGuess((m1, Option(m2), Option(dummyScalaModuleInfo("2.10.6"))))
    )
  }

  test("Comparing sbt 1.x should use Semantic Versioning semantics") {
    val m1 = "org.scala-sbt" % "util-logging_2.12" % "1.0.0"
    val m2 = "org.scala-sbt" % "util-logging_2.12" % "1.1.0"
    assert(
      EvictionWarningOptions
        .defaultGuess((m1, Option(m2), Option(dummyScalaModuleInfo("2.12.4"))))
    )
  }

  test("Comparing 2.13 libraries with pvp under Scala 3.0.0-M3 should work") {
    val m1 = "org.scodec" % "scodec-bits_2.13" % "1.1.21"
    val m2 = "org.scodec" % "scodec-bits_2.13" % "1.1.22"
    assert(
      EvictionWarningOptions
        .evalPvp((m1, Option(m2), Option(dummyScalaModuleInfo("3.0.0-M3"))))
    )
  }

  test("Comparing 2.13 libraries with guessSecondSegment under Scala 3.0.0-M3 should work") {
    val m1 = "org.scodec" % "scodec-bits_2.13" % "1.1.21"
    val m2 = "org.scodec" % "scodec-bits_2.13" % "1.1.22"
    assert(
      EvictionWarningOptions
        .guessSecondSegment((m1, Option(m2), Option(dummyScalaModuleInfo("3.0.0-M3"))))
    )
  }

  def akkaActor214 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.1.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  def akkaActor230 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  def akkaActor234 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  def scala2102 =
    ModuleID("org.scala-lang", "scala-library", "2.10.2").withConfigurations(Some("compile"))
  def scala2103 =
    ModuleID("org.scala-lang", "scala-library", "2.10.3").withConfigurations(Some("compile"))
  def scala2104 =
    ModuleID("org.scala-lang", "scala-library", "2.10.4").withConfigurations(Some("compile"))
  def commonsIo13 = ModuleID("commons-io", "commons-io", "1.3").withConfigurations(Some("compile"))
  def commonsIo14 = ModuleID("commons-io", "commons-io", "1.4").withConfigurations(Some("compile"))
  def commonsIo24 = ModuleID("commons-io", "commons-io", "2.4").withConfigurations(Some("compile"))
  def bnfparser10 =
    ModuleID("ca.gobits.bnf", "bnfparser", "1.0").withConfigurations(
      Some("compile")
    ) // uses commons-io 2.4
  def unfilteredUploads080 =
    ModuleID("net.databinder", "unfiltered-uploads", "0.8.0").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary // uses commons-io 1.4
  def bananaSesame04 =
    ModuleID("org.w3", "banana-sesame", "0.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary // uses akka-actor 2.1.4
  def akkaRemote234 =
    ModuleID("com.typesafe.akka", "akka-remote", "2.3.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary // uses akka-actor 2.3.4

  def fullOptions = EvictionWarningOptions.full
  def javaLibDirectDeps = Vector(commonsIo14, commonsIo24)
  def javaLibTransitiveDeps = Vector(unfilteredUploads080, bnfparser10)
  def scalaLibTransitiveDeps = Vector(scala2104, bananaSesame04, akkaRemote234)
  def dummyScalaModuleInfo(v: String): ScalaModuleInfo =
    ScalaModuleInfo(
      scalaFullVersion = v,
      scalaBinaryVersion = CrossVersionUtil.binaryScalaVersion(v),
      configurations = Vector.empty,
      checkExplicit = true,
      filterImplicit = false,
      overrideScalaVersion = true
    )
}
