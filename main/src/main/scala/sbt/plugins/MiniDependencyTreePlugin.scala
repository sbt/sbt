/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import sbt.PluginTrigger.AllRequirements
import sbt.Project._
import sbt.librarymanagement.Configurations.{ Compile, Test }

object MiniDependencyTreePlugin extends AutoPlugin {
  object autoImport extends MiniDependencyTreeKeys

  import autoImport._
  override def trigger: PluginTrigger = AllRequirements
  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    dependencyTreeIncludeScalaLibrary := false
  )
  override def projectSettings: Seq[Def.Setting[_]] =
    DependencyTreeSettings.coreSettings ++
      inConfig(Compile)(DependencyTreeSettings.baseBasicReportingSettings) ++
      inConfig(Test)(DependencyTreeSettings.baseBasicReportingSettings)
}
