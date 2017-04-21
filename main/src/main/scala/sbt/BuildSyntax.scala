package sbt

import sbt.internal.DslEntry
import sbt.librarymanagement.Configuration
import sbt.util.Eval

private[sbt] trait BuildSyntax {
  import language.experimental.macros
  def settingKey[T](description: String): SettingKey[T] = macro std.KeyMacro.settingKeyImpl[T]
  def taskKey[T](description: String): TaskKey[T] = macro std.KeyMacro.taskKeyImpl[T]
  def inputKey[T](description: String): InputKey[T] = macro std.KeyMacro.inputKeyImpl[T]

  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslEntry.DslDisablePlugins(ps)
  def configs(cs: Configuration*): DslEntry = DslEntry.DslConfigs(cs)
  def dependsOn(deps: Eval[ClasspathDep[ProjectReference]]*): DslEntry =
    DslEntry.DslDependsOn(deps)
  // avoid conflict with `sbt.Keys.aggregate`
  def aggregateProjects(refs: Eval[ProjectReference]*): DslEntry = DslEntry.DslAggregate(refs)
}
private[sbt] object BuildSyntax extends BuildSyntax
