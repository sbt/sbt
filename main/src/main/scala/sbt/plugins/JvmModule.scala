package sbt
package plugins

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
object JvmModule extends AutoPlugin {
  // We must be explicitly enabled
  def select = IvyModule
  
  override lazy val projectSettings: Seq[Setting[_]] = 
    Defaults.runnerSettings ++
    Defaults.paths ++
    Classpaths.jvmPublishSettings ++
    Classpaths.jvmBaseSettings ++
    Defaults.projectTasks ++
    Defaults.packageBase ++
    Defaults.compileBase ++
    Defaults.defaultConfigs
  override lazy val globalSettings: Seq[Setting[_]] =
     Defaults.globalJvmCore

  override def projectConfigurations: Seq[Configuration] = 
    Configurations.default
}