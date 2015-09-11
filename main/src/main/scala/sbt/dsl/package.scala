package sbt

import sbt.librarymanagement.Configuration

import internals.{
  DslEntry,
  DslConfigs,
  DslEnablePlugins,
  DslDisablePlugins
}

package object dsl {
  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslDisablePlugins(ps)
  def configs(cs: Configuration*): DslEntry = DslConfigs(cs)

}
