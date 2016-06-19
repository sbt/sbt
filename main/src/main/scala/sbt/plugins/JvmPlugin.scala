package sbt
package plugins

import sbt.librarymanagement.{ Configuration, Configurations }

import Def.Setting

/** A plugin representing the ability to build a JVM project.
 *
 *  Core tasks/keys:
 *  - `run`
 *  - `test`
 *  - `compile`
 *  - `fullClasspath`
 *  Core configurations
 *  - `Test`
 *  - `Compile`
 */
object JvmPlugin extends AutoPlugin {
  // We are automatically enabled for any IvyModule project.  We also require its settings
  // for ours to work.
  override def requires = IvyPlugin
  override def trigger = allRequirements
  
  override lazy val projectSettings: Seq[Setting[_]] = 
    Defaults.runnerSettings ++
    Defaults.paths ++
    Classpaths.jvmPublishSettings ++
    Classpaths.jvmBaseSettings ++
    Defaults.baseTasks ++
    Defaults.compileBase ++
    Defaults.defaultConfigs
  override lazy val globalSettings: Seq[Setting[_]] =
     Defaults.globalJvmCore

  override def projectConfigurations: Seq[Configuration] = 
    Configurations.default
}
