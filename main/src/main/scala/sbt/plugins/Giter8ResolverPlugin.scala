/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package plugins

import Def.Setting
import Keys._
import librarymanagement._

/**
 * An experimental plugin that adds the ability for Giter8 templates to be resolved
 */
object Giter8TemplatePlugin extends AutoPlugin {
  override def requires = CorePlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Setting[_]] =
    Seq(
      templateResolverInfos +=
        TemplateResolverInfo(
          ModuleID("org.scala-sbt.sbt-giter8-resolver", "sbt-giter8-resolver", "0.1.3") cross CrossVersion.binary,
          "sbtgiter8resolver.Giter8TemplateResolver"
        )
    )
}
