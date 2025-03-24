/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import sbt.librarymanagement.{ Configuration, Configurations }

import Def.Setting

/**
 * A plugin representing the ability to build a JVM project.
 *
 *  Core tasks/keys:
 *  - `run`
 *  - `test`
 *  - `compile`
 *  - `fullClasspath`
 *  Core configurations
 *  - `Test`
 *  - `Compile`
 */
object JvmPlugin extends AutoPlugin {
  // We are automatically enabled for any IvyModule project.  We also require its settings
  // for ours to work.
  override def requires = IvyPlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Setting[?]] =
    Defaults.globalJvmCore

  override lazy val buildSettings: Seq[Setting[?]] =
    Defaults.buildLevelJvmSettings

  override lazy val projectSettings: Seq[Setting[?]] =
    Defaults.runnerSettings ++
      Defaults.paths ++
      Classpaths.jvmPublishSettings ++
      Classpaths.jvmBaseSettings ++
      Defaults.baseTasks ++
      Defaults.compileBase ++
      Defaults.defaultConfigs

  override def projectConfigurations: Seq[Configuration] =
    Configurations.default
}
