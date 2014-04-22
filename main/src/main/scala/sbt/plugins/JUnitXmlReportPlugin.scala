package sbt
package plugins

import Def.Setting
import Keys._
import Project.inConfig
import Configurations.Test

/** A plugin that adds the ability for junit-xml to be generated.
 *
 *  While this plugin automatically includes its settings, to enable, you need to
 *  add:
 *  {{{
 *     testReportJunitXml in Global := true
 *  }}}
 */
object JUnitXmlReportPlugin extends AutoPlugin {
  // TODO - If testing becomes its own plugin, we only rely on the core settings.
  override def requires = JvmPlugin
  override def trigger = allRequirements
  
  // Right now we add to the global test listeners which should capture *all* tests.
  // It might be a good idea to derive this setting into specific test scopes.
  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      testListeners ++= (if( testReportJUnitXml.value ) Seq(new JUnitXmlTestsListener(target.value.getAbsolutePath)) else Nil)
    )
  override lazy val globalSettings: Seq[Setting[_]] =
    Seq(
      // TODO - in sbt 1.0, this should default to true.
      testReportJUnitXml :== false
    )

  override def projectConfigurations: Seq[Configuration] = Seq()
}