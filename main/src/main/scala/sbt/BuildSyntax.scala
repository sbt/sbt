/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.DslEntry
import sbt.librarymanagement.Configuration

private[sbt] trait BuildSyntax {
  import scala.language.experimental.macros
  def settingKey[A](description: String): SettingKey[A] = ???
  // macro std.KeyMacro.settingKeyImpl[T]
  def taskKey[A](description: String): TaskKey[A] = ???
  // macro std.KeyMacro.taskKeyImpl[T]
  def inputKey[A](description: String): InputKey[A] = ???
  // macro std.KeyMacro.inputKeyImpl[T]

  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslDisablePlugins(ps)
  def configs(cs: Configuration*): DslEntry = DslEntry.DslConfigs(cs)
  def dependsOn(deps: ClasspathDep[ProjectReference]*): DslEntry = DslEntry.DslDependsOn(deps)
  // avoid conflict with `sbt.Keys.aggregate`
  def aggregateProjects(refs: ProjectReference*): DslEntry = DslEntry.DslAggregate(refs)

  implicit def sbtStateToUpperStateOps(s: State): UpperStateOps =
    new UpperStateOps.UpperStateOpsImpl(s)
}
private[sbt] object BuildSyntax extends BuildSyntax
