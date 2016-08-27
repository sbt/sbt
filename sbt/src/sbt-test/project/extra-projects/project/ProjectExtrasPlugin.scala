import sbt._
import Keys._

object ProjectExtrasPlugin extends AutoPlugin {
  // Enable this plugin by default
  override def requires: Plugins = sbt.plugins.CorePlugin
  override def trigger = allRequirements

  override def projectExtras(proj: ProjectDefinition[_]): Seq[Project] =
    // Make sure to exclude project extras to avoid recursive generation
    if (proj.projectOrigin != ProjectOrigin.ProjectExtra) {
      val id = proj.id + "1"
      Seq(
        Project(id, file(id)).
          enablePlugins(DatabasePlugin)
      )
    }
    else Nil
}
