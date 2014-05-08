package sbt
package internals

import Def._

/** This reprsents a `Setting` expression configured by the sbt DSL. */
sealed trait DslEntry {
  /** Called by the parser.  Sets the position where this entry was defined in the build.sbt file. */
  def withPos(pos: RangePosition): DslEntry
}
object DslEntry {
  implicit def fromSettingsDef(inc: SettingsDefinition): DslEntry =
    DslSetting(inc)
  implicit def fromSettingsDef(inc: Seq[Setting[_]]): DslEntry =
    DslSetting(inc)
}

/** Represents a DSL entry which adds settings to the current project. */
sealed trait ProjectSettings extends DslEntry {
  def toSettings: Seq[Setting[_]]
}
object ProjectSettings {
  def unapply(e: DslEntry): Option[Seq[Setting[_]]] =
    e match {
      case e: ProjectSettings => Some(e.toSettings)
      case _                  => None
    }
}

/** Represents a DSL entry which manipulates the current project. */
sealed trait ProjectManipulation extends DslEntry {
  def toFunction: Project => Project
  // TODO - Should we store this?
  final def withPos(pos: RangePosition): DslEntry = this
}
object ProjectManipulation {
  def unapply(e: DslEntry): Option[Project => Project] =
    e match {
      case e: ProjectManipulation => Some(e.toFunction)
      case _                      => None
    }
}

/** this represents an actually Setting[_] or Seq[Setting[_]] configured by the sbt DSL. */
case class DslSetting(settings: SettingsDefinition) extends ProjectSettings {
  def toSettings = settings.settings
  final def withPos(pos: RangePosition): DslEntry = DslSetting(settings.settings.map(_.withPos(pos)))
}
/** this represents an `enablePlugins()` in the sbt DSL */
case class DslEnablePlugins(plugins: Seq[AutoPlugin]) extends ProjectManipulation {
  override val toFunction: Project => Project = _.enablePlugins(plugins: _*)
}
/** this represents an `disablePlugins()` in the sbt DSL */
case class DslDisablePlugins(plugins: Seq[AutoPlugin]) extends ProjectManipulation {
  override val toFunction: Project => Project = _.disablePlugins(plugins: _*)
}

