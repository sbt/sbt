package sbt
package internals

import Def._

/** This reprsents a `Setting` expression configured by the sbt DSL. */
sealed trait DslEntry
object DslEntry {
  implicit def fromSettingsDef(inc: SettingsDefinition): DslEntry =
    DslSetting(inc)
  implicit def fromSettingsDef(inc: Seq[Setting[_]]): DslEntry =
    DslSetting(inc)
}
/** this represents an actually Setting[_] or Seq[Setting[_]] configured by the sbt DSL. */
case class DslSetting(settings: SettingsDefinition) extends DslEntry
/** this represents an `enablePlugins()` in the sbt DSL */
case class DslEnablePlugins(plugins: Seq[AutoPlugin]) extends DslEntry
/** this represents an `disablePlugins()` in the sbt DSL */
case class DslDisablePlugins(plugins: Seq[AutoPlugin]) extends DslEntry

