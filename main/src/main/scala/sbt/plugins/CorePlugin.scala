/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import Def.Setting

/**
 * Plugin for core sbt-isms.
 *
 * Can control task-level parallelism, logging, etc.
 */
object CorePlugin extends AutoPlugin {
  // This is included by default
  override def trigger = allRequirements
  override def requires = empty

  override lazy val projectSettings: Seq[Setting[?]] =
    Defaults.coreDefaultSettings
  override lazy val globalSettings: Seq[Setting[?]] =
    Defaults.globalSbtCore
}
