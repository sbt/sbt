/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import sbt.PluginTrigger.AllRequirements
import sbt.Project._
import sbt.librarymanagement.Configurations.{ Compile, Test }

object DependencyReportPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = AllRequirements
  override def requires = MiniDependencyTreePlugin

  override def projectSettings: Seq[Def.Setting[?]] =
    Seq(
      inConfig(Compile)(DependencyTreeSettings.baseDependencyReportSettings),
      inConfig(Test)(DependencyTreeSettings.baseDependencyReportSettings)
    ).flatten
}
