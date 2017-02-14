import sbt._, Keys._

object ExtraProjectPluginExample2 extends AutoPlugin {
  // Enable this plugin by default
  override def requires: Plugins = sbt.plugins.CorePlugin
  override def trigger = allRequirements

  override def derivedProjects(proj: ProjectDefinition[_]): Seq[Project] =
    // Make sure to exclude project extras to avoid recursive generation
    if (proj.projectOrigin != ProjectOrigin.DerivedProject) {
      val id = proj.id + "1"
      Seq(
        Project(id, file(id)).
          enablePlugins(DatabasePlugin)
      )
    }
    else Nil
}
