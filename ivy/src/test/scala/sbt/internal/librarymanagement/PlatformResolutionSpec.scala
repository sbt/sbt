package sbt.internal.librarymanagement

import sbt.util.ShowLines
import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import sbt.librarymanagement.Platform.*

object PlatformResolutionSpec extends BaseIvySpecification {

  import TestShowLines.*

  test("None platform resolves %% as JVM") {
    cleanCache()
    val m = exampleAutoModule(platform = None)
    assert(
      update(m).configurations.head.modules.map(_.toString).mkString
        contains "com.github.scopt:scopt_2.13:4.1.0"
    )
  }

  test("sjs1 platform resolves %% as sjs1") {
    cleanCache()
    val m = exampleAutoModule(platform = Some("sjs1"))
    assert(
      update(m).configurations.head.modules.map(_.toString).mkString
        contains "com.github.scopt:scopt_sjs1_2.13"
    )
  }

  test("sjs1 platform resolves % as JVM") {
    cleanCache()
    val m = module(
      exampleModuleId("0.6.0"),
      deps = Vector(junit),
      Some(scala2_13),
      platform = Some(sjs1),
    )
    assert(
      update(m).configurations.head.modules.map(_.toString).mkString
        contains "junit:junit:4.13.1"
    )
  }

  test("None platform can specify .platform(sjs1) depenency") {
    cleanCache()
    val m = module(
      exampleModuleId("0.6.0"),
      deps = Vector(scopt.platform(sjs1)),
      Some(scala2_13),
      platform = None,
    )
    assert(
      update(m).configurations.head.modules.map(_.toString).mkString
        contains "com.github.scopt:scopt_sjs1_2.13"
    )
  }

  test("sjs1 platform can specify .platform(jvm) depenency") {
    cleanCache()
    val m = module(
      exampleModuleId("0.6.0"),
      deps = Vector(scopt.platform(jvm)),
      Some(scala2_13),
      platform = None,
    )
    assert(
      update(m).configurations.head.modules.map(_.toString).mkString
        contains "com.github.scopt:scopt_2.13:4.1.0"
    )
  }

  def exampleAutoModule(platform: Option[String]): ModuleDescriptor = module(
    exampleModuleId("0.6.0"),
    deps = Vector(scopt),
    Some(scala2_13),
    platform = platform,
  )

  def exampleModuleId(v: String): ModuleID = ("com.example" % "foo" % v % Compile)
  def scopt = ("com.github.scopt" %% "scopt" % "4.1.0" % Compile)
  def junit = ("junit" % "junit" % "4.13.1" % Compile)
  override val resolvers = Vector(
    Resolver.mavenCentral,
  )
}
