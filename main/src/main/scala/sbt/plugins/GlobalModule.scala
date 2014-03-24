package sbt
package plugins

import Def.Setting

/**
 * Plugin for core sbt-isms.
 *
 * Can control task-level paralleism, logging, etc.
 */
object GlobalModule extends AutoPlugin {
  // This is included by default
  def requires = empty
  def trigger = allRequirements
  
  override lazy val projectSettings: Seq[Setting[_]] = 
    Defaults.coreDefaultSettings
  override lazy val globalSettings: Seq[Setting[_]] =
     Defaults.globalSbtCore
}