/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

object DependencyTreePlugin extends AutoPlugin {
  object autoImport extends DependencyTreeKeys
  override def trigger = AllRequirements
  override def requires = MiniDependencyTreePlugin

  val configurations = Vector(Compile, Test, IntegrationTest, Runtime, Provided, Optional)

  // MiniDependencyTreePlugin provides baseBasicReportingSettings for Compile and Test
  override def projectSettings: Seq[Def.Setting[_]] =
    ((configurations diff Vector(Compile, Test)) flatMap { config =>
      inConfig(config)(DependencyTreeSettings.baseBasicReportingSettings)
    }) ++
      (configurations flatMap { config =>
        inConfig(config)(DependencyTreeSettings.baseFullReportingSettings)
      })
}
