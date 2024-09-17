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

private[sbt] trait BuildSyntax:
  import scala.language.experimental.macros

  /**
   * Creates a new Project.  This is a macro that expects to be assigned directly to a val.
   * The name of the val is used as the project ID and the name of the base directory of the project.
   */
  inline def project: Project = ${ std.KeyMacro.projectImpl }
  inline def projectMatrix: ProjectMatrix = ${ ProjectMatrix.projectMatrixImpl }
  inline def settingKey[A1](inline description: String): SettingKey[A1] =
    ${ std.KeyMacro.settingKeyImpl[A1]('description) }
  inline def taskKey[A1](inline description: String): TaskKey[A1] =
    ${ std.KeyMacro.taskKeyImpl[A1]('description) }
  inline def inputKey[A1](inline description: String): InputKey[A1] =
    ${ std.KeyMacro.inputKeyImpl[A1]('description) }

  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslDisablePlugins(ps)
  def configs(cs: Configuration*): DslEntry = DslEntry.DslConfigs(cs)
  def dependsOn(deps: ClasspathDep[ProjectReference]*): DslEntry = DslEntry.DslDependsOn(deps)
  // avoid conflict with `sbt.Keys.aggregate`
  def aggregateProjects(refs: ProjectReference*): DslEntry = DslEntry.DslAggregate(refs)

  implicit def sbtStateToUpperStateOps(s: State): UpperStateOps =
    new UpperStateOps.UpperStateOpsImpl(s)
end BuildSyntax

private[sbt] object BuildSyntax extends BuildSyntax
