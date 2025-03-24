/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.DslEntry
import sbt.librarymanagement.Configuration

trait BareBuildSyntax:
  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslDisablePlugins(ps)
  def configs(cs: Configuration*): DslEntry = DslEntry.DslConfigs(cs)
  def dependsOn(deps: ClasspathDep[ProjectReference]*): DslEntry = DslEntry.DslDependsOn(deps)
  // avoid conflict with `sbt.Keys.aggregate`
  def aggregateProjects(refs: ProjectReference*): DslEntry = DslEntry.DslAggregate(refs)
end BareBuildSyntax

object BareBuildSyntax extends BareBuildSyntax
