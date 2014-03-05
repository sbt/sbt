package sbt
package plugins

import Def.Setting

/**
 * Plugin that enables resolving artifacts via ivy.
 *
 * Core Tasks
 * - `update`
 * - `makePom`
 * - `publish`
 * - `artifacts`
 * - `publishedArtifacts`
 */
object IvyModule extends AutoPlugin {
  // We must be explicitly enabled
  def select = GlobalModule
  
  override lazy val projectSettings: Seq[Setting[_]] = 
    Classpaths.ivyPublishSettings ++ Classpaths.ivyBaseSettings
  override lazy val globalSettings: Seq[Setting[_]] =
     Defaults.globalIvyCore
}