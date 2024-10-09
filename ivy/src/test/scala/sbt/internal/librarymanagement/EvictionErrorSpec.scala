package sbt.internal.librarymanagement

import sbt.librarymanagement._
import sbt.internal.librarymanagement.cross.CrossVersionUtil
import sbt.librarymanagement.syntax._
import sbt.util.Level

object EvictionErrorSpec extends BaseIvySpecification {
  // This is a specification to check the eviction errors

  import TestShowLines.*
  import EvictionError.given

  test("Eviction error should detect binary incompatible Scala libraries") {
    val deps = Vector(`scala2.10.4`, `akkaActor2.1.4`, `akkaActor2.3.0`)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(EvictionError(report, m, oldAkkaPvp).incompatibleEvictions.size == 1)
  }

  test("it should print out message about the eviction") {
    val deps = Vector(`scala2.10.4`, `akkaActor2.1.4`, `akkaActor2.3.0`)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(
      EvictionError(report, m, oldAkkaPvp).lines ==
        List(
          "found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* com.typesafe.akka:akka-actor_2.10:2.3.0 (pvp) is selected over 2.1.4",
          "\t    +- com.example:foo:0.1.0                              (depends on 2.1.4)",
          ""
        )
    )
  }

  test("it should print out message including the transitive dependencies") {
    val deps = Vector(`scala2.10.4`, `bananaSesame0.4`, `akkaRemote2.3.4`)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(
      EvictionError(report, m, oldAkkaPvp).lines ==
        List(
          "found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 (pvp) is selected over 2.1.4",
          "\t    +- com.typesafe.akka:akka-remote_2.10:2.3.4           (depends on 2.3.4)",
          "\t    +- org.w3:banana-rdf_2.10:0.4                         (depends on 2.1.4)",
          "\t    +- org.w3:banana-sesame_2.10:0.4                      (depends on 2.1.4)",
          ""
        )
    )
  }

  test("it should be able to emulate eviction warnings") {
    val deps = Vector(`scala2.10.4`, `bananaSesame0.4`, `akkaRemote2.3.4`)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    assert(
      EvictionError(report, m, Nil, "pvp", "early-semver", Level.Warn).toAssumedLines ==
        List(
          "found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 (pvp?) is selected over 2.1.4",
          "\t    +- com.typesafe.akka:akka-remote_2.10:2.3.4           (depends on 2.3.4)",
          "\t    +- org.w3:banana-rdf_2.10:0.4                         (depends on 2.1.4)",
          "\t    +- org.w3:banana-sesame_2.10:0.4                      (depends on 2.1.4)",
          ""
        )
    )
  }

  test("it should detect Semantic Versioning violations") {
    val deps = Vector(`scala2.13.3`, `http4s0.21.11`, `cats-effect3.0.0-M4`)
    val m = module(defaultModuleId, deps, Some("2.13.3"))
    val report = ivyUpdate(m)
    assert(
      EvictionError(report, m, Nil).lines ==
        List(
          "found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
          "",
          "\t* org.typelevel:cats-effect_2.13:3.0.0-M4 (early-semver) is selected over {2.0.0, 2.2.0}",
          "\t    +- com.example:foo:0.1.0                              (depends on 3.0.0-M4)",
          "\t    +- co.fs2:fs2-core_2.13:2.4.5                         (depends on 2.2.0)",
          "\t    +- org.http4s:http4s-core_2.13:0.21.11                (depends on 2.2.0)",
          "\t    +- io.chrisdavenport:vault_2.13:2.0.0                 (depends on 2.0.0)",
          "\t    +- io.chrisdavenport:unique_2.13:2.0.0                (depends on 2.0.0)",
          ""
        )
    )
  }

  test("it should selectively allow opt-out from the error") {
    val deps = Vector(`scala2.13.3`, `http4s0.21.11`, `cats-effect3.0.0-M4`)
    val m = module(defaultModuleId, deps, Some("2.13.3"))
    val report = ivyUpdate(m)
    val overrideRules = List("org.typelevel" %% "cats-effect" % "always")
    assert(EvictionError(report, m, overrideRules).incompatibleEvictions.isEmpty)
  }

  test("it should selectively allow opt-out from the error despite assumed scheme") {
    val deps = Vector(`scala2.12.17`, `akkaActor2.6.0`, `swagger-akka-http1.4.0`)
    val m = module(defaultModuleId, deps, Some("2.12.17"))
    val report = ivyUpdate(m)
    val overrideRules = List("org.scala-lang.modules" %% "scala-java8-compat" % "always")
    assert(
      EvictionError(
        report = report,
        module = m,
        schemes = overrideRules,
        assumedVersionScheme = "early-semver",
        assumedVersionSchemeJava = "always",
        assumedEvictionErrorLevel = Level.Error,
      ).assumedIncompatibleEvictions.isEmpty
    )
  }

  // older Akka was on pvp
  def oldAkkaPvp = List("com.typesafe.akka" % "*" % "pvp")

  lazy val `akkaActor2.1.4` =
    ModuleID("com.typesafe.akka", "akka-actor", "2.1.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  lazy val `akkaActor2.3.0` =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  lazy val `akkaActor2.6.0` =
    ModuleID("com.typesafe.akka", "akka-actor", "2.6.0").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary
  lazy val `scala2.10.4` =
    ModuleID("org.scala-lang", "scala-library", "2.10.4").withConfigurations(Some("compile"))
  lazy val `scala2.12.17` =
    ModuleID("org.scala-lang", "scala-library", "2.12.17").withConfigurations(Some("compile"))
  lazy val `scala2.13.3` =
    ModuleID("org.scala-lang", "scala-library", "2.13.3").withConfigurations(Some("compile"))
  lazy val `bananaSesame0.4` =
    ModuleID("org.w3", "banana-sesame", "0.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary // uses akka-actor 2.1.4
  lazy val `akkaRemote2.3.4` =
    ModuleID("com.typesafe.akka", "akka-remote", "2.3.4").withConfigurations(
      Some("compile")
    ) cross CrossVersion.binary // uses akka-actor 2.3.4
  lazy val `http4s0.21.11` =
    ("org.http4s" %% "http4s-blaze-server" % "0.21.11").withConfigurations(Some("compile"))
  // https://repo1.maven.org/maven2/org/typelevel/cats-effect_2.13/3.0.0-M4/cats-effect_2.13-3.0.0-M4.pom
  // is published with early-semver
  lazy val `cats-effect3.0.0-M4` =
    ("org.typelevel" %% "cats-effect" % "3.0.0-M4").withConfigurations(Some("compile"))
  lazy val `cats-parse0.1.0` =
    ("org.typelevel" %% "cats-parse" % "0.1.0").withConfigurations(Some("compile"))
  lazy val `cats-parse0.2.0` =
    ("org.typelevel" %% "cats-parse" % "0.2.0").withConfigurations(Some("compile"))
  lazy val `swagger-akka-http1.4.0` =
    ("com.github.swagger-akka-http" %% "swagger-akka-http" % "1.4.0")
      .withConfigurations(Some("compile"))

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
