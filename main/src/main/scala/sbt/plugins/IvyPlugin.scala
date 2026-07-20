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
import Keys.*
import sbt.io.IO
import sbt.io.syntax.*

/**
 * Plugin that enables resolving artifacts via ivy.
 *
 * Core Tasks
 * - `update`
 * - `makePom`
 * - `publish`
 * - `artifacts`
 * - `publishedArtifacts`
 */
object IvyPlugin extends AutoPlugin {
  // We are automatically included on everything that has the global module,
  // which is automatically included on everything.
  override def requires = CorePlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Setting[?]] =
    Defaults.globalIvyCore
  override lazy val buildSettings: Seq[Setting[?]] =
    Seq(
      publish / clean := Def.uncached {
        IO.delete(stagingDirectory.value)
        IO.delete((ThisBuild / baseDirectory).value / "target" / "sona-bundle")
      },
    )
  override lazy val projectSettings: Seq[Setting[?]] =
    Classpaths.ivyPublishSettings ++ Classpaths.ivyBaseSettings

}
