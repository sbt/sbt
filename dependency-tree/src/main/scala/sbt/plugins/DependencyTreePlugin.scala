/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import scala.annotation.nowarn

object DependencyTreePlugin extends AutoPlugin {
  object autoImport extends DependencyTreeKeys
  override def trigger = AllRequirements
  override def requires = MiniDependencyTreePlugin

  @nowarn
  val configurations = Vector(Compile, Test, IntegrationTest, Runtime, Provided, Optional)

  // MiniDependencyTreePlugin provides baseBasicReportingSettings for Compile and Test
  override lazy val projectSettings: Seq[Def.Setting[?]] =
    configurations.diff(Vector(Compile, Test)).flatMap { config =>
      inConfig(config)(DependencyTreeSettings.baseBasicReportingSettings)
    } ++
      configurations.flatMap { config =>
        inConfig(config)(DependencyTreeSettings.baseFullReportingSettings)
      }
}
