package sbt.internal.librarymanagement

import org.scalatest.LoneElement._
import sbt.util.ShowLines
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._

abstract class ResolutionSpec extends AbstractEngineSpec {

  import TestShowLines.*

  test("Resolving the same module twice should work") {
    cleanCache()
    val m = module(
      exampleModuleId("0.1.0"),
      Vector(commonsIo13),
      Some("2.10.2")
    )
    val report = update(m)
    cleanCachedResolutionCache(m)
    val _ = update(m)
    // first resolution creates the minigraph
    println(report)
    // second resolution reads from the minigraph
    println(report.configurations.head.modules.head.artifacts)
    assert(report.configurations.size == 3)
  }

  test("Resolving the unsolvable module should should not work") {
    // log.setLevel(Level.Debug)
    val m = module(
      exampleModuleId("0.2.0"),
      Vector(mavenCayennePlugin302),
      Some("2.10.2")
    )
    updateEither(m) match {
      case Right(_) => sys.error("this should've failed")
      case Left(uw) =>
        println(uw.lines.mkString("\n"))
    }
    updateEither(m) match {
      case Right(_) => sys.error("this should've failed 2")
      case Left(uw) =>
        List(
          "\n\tNote: Unresolved dependencies path:",
          "\t\tfoundrylogic.vpp:vpp:2.2.1",
          "\t\t  +- org.apache.cayenne:cayenne-tools:3.0.2",
          "\t\t  +- org.apache.cayenne.plugins:maven-cayenne-plugin:3.0.2",
          "\t\t  +- com.example:foo:0.2.0"
        ) foreach { line =>
          assert(uw.lines.contains[String](line))
        }
    }
  }

  // https://github.com/sbt/sbt/issues/2046
  // data-avro:1.9.40 depends on avro:1.4.0, which depends on netty:3.2.1.Final.
  // avro:1.4.0 will be evicted by avro:1.7.7.
  // #2046 says that netty:3.2.0.Final is incorrectly evicted by netty:3.2.1.Final
  test("Resolving a module with a pseudo-conflict should work") {
    // log.setLevel(Level.Debug)
    cleanCache()
    val m = module(
      exampleModuleId("0.3.0"),
      Vector(avro177, dataAvro1940, netty320),
      Some("2.10.2")
    )
    // first resolution creates the minigraph
    val _ = update(m)
    cleanCachedResolutionCache(m)
    // second resolution reads from the minigraph
    val report = update(m)
    val modules: Seq[String] = report.configurations.head.modules map { _.toString }
    assert(modules exists { (x: String) =>
      x contains """org.jboss.netty:netty:3.2.0.Final"""
    })
    assert(!(modules exists { (x: String) =>
      x contains """org.jboss.netty:netty:3.2.1.Final"""
    }))
  }

  test("Resolving a module with sbt cross build should work") {
    cleanCache()
    val attributes013 = Map("e:sbtVersion" -> "0.13", "e:scalaVersion" -> "2.10")
    val attributes10 = Map("e:sbtVersion" -> "1.0", "e:scalaVersion" -> "2.12")
    val module013 = module(
      exampleModuleId("0.4.0"),
      Vector(sbtRelease.withExtraAttributes(attributes013)),
      Some("2.10.6")
    )
    val module10 = module(
      exampleModuleId("0.4.1"),
      Vector(sbtRelease.withExtraAttributes(attributes10)),
      Some("2.12.3")
    )
    assert(
      update(module013).configurations.head.modules.map(_.toString)
        contains "com.github.gseitz:sbt-release:1.0.6 (scalaVersion=2.10, sbtVersion=0.13)"
    )
    assert(
      update(module10).configurations.head.modules.map(_.toString)
        contains "com.github.gseitz:sbt-release:1.0.6 (scalaVersion=2.12, sbtVersion=1.0)"
    )
  }

  def exampleModuleId(v: String): ModuleID = ("com.example" % "foo" % v % Compile)

  def commonsIo13 = ("commons-io" % "commons-io" % "1.3" % Compile)
  def mavenCayennePlugin302 =
    ("org.apache.cayenne.plugins" % "maven-cayenne-plugin" % "3.0.2" % Compile)
  def avro177 = ("org.apache.avro" % "avro" % "1.7.7" % Compile)
  def dataAvro1940 =
    ("com.linkedin.pegasus" % "data-avro" % "1.9.40" % Compile)
  def netty320 = ("org.jboss.netty" % "netty" % "3.2.0.Final" % Compile)
  def sbtRelease = ("com.github.gseitz" % "sbt-release" % "1.0.6" % Compile)

  def defaultOptions = EvictionWarningOptions.default
}
