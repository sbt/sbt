package sbt
package plugins

import Def.Setting
import Keys._

/** An experimental plugin that adds the ability for junit-xml to be generated.
 *
 *  To disable this plugin, you need to add:
 *  {{{
 *     val myProject = project in file(".") disablePlugins (plugins.JunitXmlReportPlugin)
 *  }}}
 *
 *  Note:  Using AutoPlugins to enable/disable build features is experimental in sbt 0.13.5.
 */
object JUnitXmlReportPlugin extends AutoPlugin {
  // TODO - If testing becomes its own plugin, we only rely on the core settings.
  override def requires = JvmPlugin
  override def trigger = allRequirements
  
  // Right now we add to the global test listeners which should capture *all* tests.
  // It might be a good idea to derive this setting into specific test scopes.
  override lazy val projectSettings: Seq[Setting[_]] =
    Seq(
      testListeners += new JUnitXmlTestsListener(target.value.getAbsolutePath)
    )
}
