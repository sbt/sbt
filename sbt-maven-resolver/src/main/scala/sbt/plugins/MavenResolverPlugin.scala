package sbt
package plugins

import Keys._

object MavenResolverPlugin extends AutoPlugin {
  override def requires = IvyPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    updateOptions := updateOptions.value.withResolverConverter(MavenResolverConverter.converter)
  )
}
