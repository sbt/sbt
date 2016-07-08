package sbt
package plugins

import Def.Setting

/**
 * Plugin for core sbt-isms.
 *
 * Can control task-level parallelism, logging, etc.
 */
object CorePlugin extends AutoPlugin {
  // This is included by default
  override def trigger = allRequirements
  override def requires = empty

  override lazy val projectSettings: Seq[Setting[_]] =
    Defaults.coreDefaultSettings
  override lazy val globalSettings: Seq[Setting[_]] =
    Defaults.globalSbtCore
}