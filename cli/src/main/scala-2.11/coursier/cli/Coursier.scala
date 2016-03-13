package coursier
package cli

import caseapp._

object Coursier extends CommandAppOf[CoursierCommand] {
  override def appName = "Coursier"
  override def progName = "coursier"
  override def appVersion = coursier.util.Properties.version
}
