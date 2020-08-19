/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import Def.Setting
import sbt.internal.GlobalDefaults

/**
 * Plugin for core sbt-isms.
 *
 * Can control task-level parallelism, logging, etc.
 */
object CorePlugin extends AutoPlugin {
  // This is included by default
  override def trigger = allRequirements
  override def requires = empty

  override lazy val projectSettings: Seq[Setting[_]] =
    GlobalDefaults.coreDefaultSettings
  override lazy val globalSettings: Seq[Setting[_]] =
    GlobalDefaults.globalSbtCore
}
