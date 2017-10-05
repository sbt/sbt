/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.specs2._
import mutable.Specification
import sbt.util.Logger

object PluginsTest extends Specification {
  import AI._

  "Auto plugin" should {
    "enable plugins with trigger=allRequirements AND requirements met" in {
      deducePlugin(A && B, log) must contain(Q)
    }
    "enable transitive plugins with trigger=allRequirements AND requirements met" in {
      deducePlugin(A && B, log) must contain(R)
    }
    "order enable plugins after required plugins" in {
      val ns = deducePlugin(A && B, log)
      ((ns indexOf Q) must beGreaterThan(ns indexOf A)) and
        ((ns indexOf Q) must beGreaterThan(ns indexOf B)) and
        ((ns indexOf R) must beGreaterThan(ns indexOf A)) and
        ((ns indexOf R) must beGreaterThan(ns indexOf B)) and
        ((ns indexOf R) must beGreaterThan(ns indexOf Q))
    }
    "not enable plugins with trigger=allRequirements but conflicting requirements" in {
      deducePlugin(A && B, log) must not contain (S)
    }
    "enable plugins that are required by the requested plugins" in {
      val ns = deducePlugin(Q, log)
      (ns must contain(A)) and
        (ns must contain(B))
    }
    "throw an AutoPluginException on conflicting requirements" in {
      deducePlugin(S, log) must throwAn[AutoPluginException](
        message = """Contradiction in enabled plugins:
  - requested: sbt.AI\$S
  - enabled: sbt.AI\$S, sbt.AI\$Q, sbt.AI\$R, sbt.AI\$B, sbt.AI\$A
  - conflict: sbt.AI\$R is enabled by sbt.AI\$Q; excluded by sbt.AI\$S""")
    }
    "generates a detailed report on conflicting requirements" in {
      deducePlugin(T && U, log) must throwAn[AutoPluginException](message =
        """Contradiction in enabled plugins:
  - requested: sbt.AI\$T && sbt.AI\$U
  - enabled: sbt.AI\$U, sbt.AI\$T, sbt.AI\$A, sbt.AI\$Q, sbt.AI\$R, sbt.AI\$B
  - conflict: sbt.AI\$Q is enabled by sbt.AI\$A && sbt.AI\$B; required by sbt.AI\$T, sbt.AI\$R; excluded by sbt.AI\$U
  - conflict: sbt.AI\$R is enabled by sbt.AI\$Q; excluded by sbt.AI\$T""")
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
