package sbt

import internals.{
  DslEntry,
  DslConfigs,
  DslEnablePlugins,
  DslDisablePlugins,
  DslDependsOn,
  DslAggregate
}

package object dsl {
  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslDisablePlugins(ps)
  def configs(cs: Configuration*): DslEntry = DslConfigs(cs)
  def dependsOn(deps: ClasspathDep[ProjectReference]*): DslEntry = DslDependsOn(deps)
  // avoid conflict with `sbt.Keys.aggregate`
  def aggregateProjects(refs: ProjectReference*): DslEntry = DslAggregate(refs)
}
