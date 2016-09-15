
### Synthetic subprojects

sbt 0.13.13 adds support for `AutoPlugin`s to generate
synthetic subprojects. To generate subprojects, override `extraProjects`
method as follows:

    import sbt._
    import Keys._

    object ExtraProjectPluginExample extends AutoPlugin {
      override def extraProjects: Seq[Project] =
        List("foo", "bar", "baz") map generateProject

      def generateProject(id: String): Project =
        Project(id, file(id)).
          settings(
            name := id
          )
    }

In addition, subprojects may be derived from an existing subproject
by overriding `derivedProjects`.

    import sbt._
    import Keys._

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
