package sbt

import internals.{
  DslEntry,
  DslSetting,
  DslEnablePlugins,
  DslDisablePlugins
}

package object dsl {
  def enablePlugins(ps: AutoPlugin*): DslEntry = DslEnablePlugins(ps)
  def disablePlugins(ps: AutoPlugin*): DslEntry = DslDisablePlugins(ps)
}