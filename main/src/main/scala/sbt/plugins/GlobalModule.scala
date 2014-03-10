package sbt
package plugins

import Def.Setting

/**
 * Plugin for core sbt-isms.
 *
 * Can control task-level paralleism, logging, etc.
 */
object GlobalModule extends AutoPlugin {
  // We must be explicitly enabled
  def select = Plugins.empty
  
  override lazy val projectSettings: Seq[Setting[_]] = 
    Defaults.coreDefaultSettings
  override lazy val globalSettings: Seq[Setting[_]] =
     Defaults.globalSbtCore
}