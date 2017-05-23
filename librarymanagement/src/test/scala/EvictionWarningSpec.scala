package sbt.librarymanagement

import sbt.internal.librarymanagement.BaseIvySpecification

class EvictionWarningSpec extends BaseIvySpecification {
  // This is a specification to check the eviction warnings

  """Eviction of non-overridden scala-library whose scalaVersion
  """ should "be detected" in scalaVersionWarn1()
  it should "not be detected if it's disabled" in scalaVersionWarn2()
  it should "print out message about the eviction" in scalaVersionWarn3()
  it should "print out message about the eviction with callers" in scalaVersionWarn4()

  """Non-eviction of overridden scala-library whose scalaVersion
  """ should "not be detected if it's enabled" in scalaVersionWarn5()
  it should "not be detected if it's disabled" in scalaVersionWarn6()

  """Including two (suspect) binary incompatible Java libraries to direct dependencies
  """ should "be detected as eviction" in javaLibWarn1()
  it should "not be detected if it's disabled" in javaLibWarn2()
  it should "print out message about the eviction" in javaLibWarn3()
  it should "print out message about the eviction with callers" in javaLibWarn4()

  """Including two (suspect) binary compatible Java libraries to direct dependencies
  """ should "not be detected as eviction" in javaLibNoWarn1()
  it should "print out message about the eviction" in javaLibNoWarn2()

  """Including two (suspect) transitively binary incompatible Java libraries to direct dependencies
  """ should "be detected as eviction" in javaLibTransitiveWarn2()

  //it should "print out message about the eviction if it's enabled" in javaLibTransitiveWarn3()

  """Including two (suspect) binary incompatible Scala libraries to direct dependencies
  """ should "be detected as eviction" in scalaLibWarn1()
  it should "print out message about the eviction" in scalaLibWarn2()

  """Including two (suspect) binary compatible Scala libraries to direct dependencies
  """ should "not be detected as eviction" in scalaLibNoWarn1()
  it should "print out message about the eviction" in scalaLibNoWarn2()

  """Including two (suspect) transitively binary incompatible Scala libraries to direct dependencies
  """ should "be detected as eviction" in scalaLibTransitiveWarn2()
  it should "print out message about the eviction if it's enabled" in scalaLibTransitiveWarn3()

  def akkaActor214 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.1.4").withConfigurations(Some("compile")) cross CrossVersion.binary
  def akkaActor230 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.0").withConfigurations(Some("compile")) cross CrossVersion.binary
  def akkaActor234 =
    ModuleID("com.typesafe.akka", "akka-actor", "2.3.4").withConfigurations(Some("compile")) cross CrossVersion.binary
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
    ModuleID("ca.gobits.bnf", "bnfparser", "1.0").withConfigurations(Some("compile")) // uses commons-io 2.4
  def unfilteredUploads080 =
    ModuleID("net.databinder", "unfiltered-uploads", "0.8.0").withConfigurations(Some("compile")) cross CrossVersion.binary // uses commons-io 1.4
  def bananaSesame04 =
    ModuleID("org.w3", "banana-sesame", "0.4").withConfigurations(Some("compile")) cross CrossVersion.binary // uses akka-actor 2.1.4
  def akkaRemote234 =
    ModuleID("com.typesafe.akka", "akka-remote", "2.3.4").withConfigurations(Some("compile")) cross CrossVersion.binary // uses akka-actor 2.3.4

  def defaultOptions = EvictionWarningOptions.default

  import sbt.util.ShowLines._

  def scalaVersionDeps = Vector(scala2102, akkaActor230)

  def scalaVersionWarn1() = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).scalaEvictions should have size (1)
  }

  def scalaVersionWarn2() = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withWarnScalaVersionEviction(false), report, log).scalaEvictions should have size (0)
  }

  def scalaVersionWarn3() = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withShowCallers(false), report, log).lines shouldBe
      List(
        "Scala version was updated by one of library dependencies:",
        "\t* org.scala-lang:scala-library:2.10.3 is selected over 2.10.2",
        "",
        "To force scalaVersion, add the following:",
        "\tivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }",
        "Run 'evicted' to see detailed eviction warnings"
      )
  }

  def scalaVersionWarn4() = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"), overrideScalaVersion = false)
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines shouldBe
      List(
        "Scala version was updated by one of library dependencies:",
        "\t* org.scala-lang:scala-library:2.10.3 is selected over 2.10.2",
        "\t    +- com.typesafe.akka:akka-actor_2.10:2.3.0            (depends on 2.10.3)",
        "\t    +- com.example:foo:0.1.0                              (depends on 2.10.2)",
        "",
        "To force scalaVersion, add the following:",
        "\tivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }",
        "Run 'evicted' to see detailed eviction warnings"
      )
  }

  def scalaVersionWarn5() = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).scalaEvictions should have size (0)
  }

  def scalaVersionWarn6() = {
    val m = module(defaultModuleId, scalaVersionDeps, Some("2.10.2"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withWarnScalaVersionEviction(false), report, log).scalaEvictions should have size (0)
  }

  def javaLibDirectDeps = Vector(commonsIo14, commonsIo24)

  def javaLibWarn1() = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions should have size (1)
  }

  def javaLibWarn2() = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m,
                    defaultOptions
                      .withWarnDirectEvictions(false)
                      .withWarnTransitiveEvictions(false),
                    report,
                    log).reportedEvictions should have size (0)
  }

  def javaLibWarn3() = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines shouldBe
      List(
        "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* commons-io:commons-io:2.4 is selected over 1.4",
        "\t    +- com.example:foo:0.1.0                              (depends on 1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings"
      )
  }

  def javaLibWarn4() = {
    val m = module(defaultModuleId, javaLibDirectDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions.withShowCallers(true), report, log).lines shouldBe
      List(
        "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* commons-io:commons-io:2.4 is selected over 1.4",
        "\t    +- com.example:foo:0.1.0                              (depends on 1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings"
      )
  }

  def javaLibNoWarn1() = {
    val deps = Vector(commonsIo14, commonsIo13)
    val m = module(defaultModuleId, deps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions should have size (0)
  }

  def javaLibNoWarn2() = {
    val deps = Vector(commonsIo14, commonsIo13)
    val m = module(defaultModuleId, deps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines shouldBe Nil
  }

  def javaLibTransitiveDeps = Vector(unfilteredUploads080, bnfparser10)

  def javaLibTransitiveWarn2() = {
    val m = module(defaultModuleId, javaLibTransitiveDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions should have size (1)
  }

  def javaLibTransitiveWarn3() = {
    val m = module(defaultModuleId, javaLibTransitiveDeps, Some("2.10.3"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines shouldBe
      List(
        "There may be incompatibilities among your library dependencies.",
        "Here are some of the libraries that were evicted:",
        "\t* commons-io:commons-io:1.4 -> 2.4 (caller: ca.gobits.bnf:bnfparser:1.0, net.databinder:unfiltered-uploads_2.10:0.8.0)"
      )
  }

  def scalaLibWarn1() = {
    val deps = Vector(scala2104, akkaActor214, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions should have size (1)
  }

  def scalaLibWarn2() = {
    val deps = Vector(scala2104, akkaActor214, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines shouldBe
      List(
        "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 is selected over 2.1.4",
        "\t    +- com.example:foo:0.1.0                              (depends on 2.1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings"
      )
  }

  def scalaLibNoWarn1() = {
    val deps = Vector(scala2104, akkaActor230, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions should have size (0)
  }

  def scalaLibNoWarn2() = {
    val deps = Vector(scala2104, akkaActor230, akkaActor234)
    val m = module(defaultModuleId, deps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines shouldBe Nil
  }

  def scalaLibTransitiveDeps = Vector(scala2104, bananaSesame04, akkaRemote234)

  def scalaLibTransitiveWarn2() = {
    val m = module(defaultModuleId, scalaLibTransitiveDeps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).reportedEvictions should have size (1)
  }

  def scalaLibTransitiveWarn3() = {
    val m = module(defaultModuleId, scalaLibTransitiveDeps, Some("2.10.4"))
    val report = ivyUpdate(m)
    EvictionWarning(m, defaultOptions, report, log).lines shouldBe
      List(
        "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:",
        "",
        "\t* com.typesafe.akka:akka-actor_2.10:2.3.4 is selected over 2.1.4",
        "\t    +- com.typesafe.akka:akka-remote_2.10:2.3.4           (depends on 2.3.4)",
        "\t    +- org.w3:banana-rdf_2.10:0.4                         (depends on 2.1.4)",
        "\t    +- org.w3:banana-sesame_2.10:0.4                      (depends on 2.1.4)",
        "",
        "Run 'evicted' to see detailed eviction warnings"
      )
  }
}
