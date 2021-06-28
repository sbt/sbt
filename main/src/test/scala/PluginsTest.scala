/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.util.Logger

object PluginsTest extends verify.BasicTestSuite {
  import AI._

  test("Auto plugin should enable plugins with trigger=allRequirements AND requirements met") {
    assert(deducePlugin(A && B, log).contains(Q))
  }

  test("it should enable transitive plugins with trigger=allRequirements AND requirements met") {
    assert(deducePlugin(A && B, log) contains (R))
  }

  test("it should order enable plugins after required plugins") {
    val ns = deducePlugin(A && B, log)
    assert((ns indexOf Q) > (ns indexOf A))
    assert((ns indexOf Q) > (ns indexOf B))
    assert((ns indexOf R) > (ns indexOf A))
    assert((ns indexOf R) > (ns indexOf B))
    assert((ns indexOf R) > (ns indexOf Q))
  }

  test("it should not enable plugins with trigger=allRequirements but conflicting requirements") {
    assert(!deducePlugin(A && B, log).contains(S))
  }

  test("it should enable plugins that are required by the requested plugins") {
    val ns = deducePlugin(Q, log)
    assert(ns.contains(A))
    assert(ns.contains(B))
  }

  test("it should throw an AutoPluginException on conflicting requirements") {
    try {
      deducePlugin(S, log)
    } catch {
      case e: AutoPluginException =>
        assertEquals(
          s"""Contradiction in enabled plugins:
  - requested: sbt.AI$$S
  - enabled: sbt.AI$$S, sbt.AI$$Q, sbt.AI$$R, sbt.AI$$B, sbt.AI$$A
  - conflict: sbt.AI$$R is enabled by sbt.AI$$Q; excluded by sbt.AI$$S""",
          e.message
        )
    }
  }

  test("it should generate a detailed report on conflicting requirements") {
    try {
      deducePlugin(T && U, log)
    } catch {
      case e: AutoPluginException =>
        assertEquals(
          s"""Contradiction in enabled plugins:
  - requested: sbt.AI$$T && sbt.AI$$U
  - enabled: sbt.AI$$U, sbt.AI$$T, sbt.AI$$A, sbt.AI$$Q, sbt.AI$$R, sbt.AI$$B
  - conflict: sbt.AI$$Q is enabled by sbt.AI$$A && sbt.AI$$B; required by sbt.AI$$T, sbt.AI$$R; excluded by sbt.AI$$U
  - conflict: sbt.AI$$R is enabled by sbt.AI$$Q; excluded by sbt.AI$$T""",
          e.message
        )
    }
  }
}

object AI {
  lazy val allPlugins: List[AutoPlugin] = List(A, B, Q, R, S, T, U)
  lazy val deducePlugin = Plugins.deducer(allPlugins)
  lazy val log = Logger.Null

  object A extends AutoPlugin { override def requires = empty }
  object B extends AutoPlugin { override def requires = empty }

  object Q extends AutoPlugin {
    override def requires: Plugins = A && B
    override def trigger = allRequirements
  }

  object R extends AutoPlugin {
    override def requires = Q
    override def trigger = allRequirements
  }

  object S extends AutoPlugin {
    override def requires = Q && !R
    override def trigger = allRequirements
  }

  // This is an opt-in plugin with a requirement
  // Unless explicitly loaded by the build user, this will not be activated.
  object T extends AutoPlugin {
    override def requires = Q && !R
  }

  // This is an opt-in plugin with a requirement
  // Unless explicitly loaded by the build user, this will not be activated.
  object U extends AutoPlugin {
    override def requires = A && !Q
  }
}
