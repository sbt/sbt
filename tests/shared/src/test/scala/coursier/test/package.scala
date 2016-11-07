package coursier

package object test {

  implicit class DependencyOps(val underlying: Dependency) extends AnyVal {
    def withCompileScope: Dependency = underlying.copy(configuration = "compile")
    def withJarAttributeType: Dependency = underlying.copy(attributes = underlying.attributes.copy(`type` = "jar"))
  }

  implicit class ResolutionOps(val underlying: Resolution) extends AnyVal {

    // The content of these fields is typically not validated in the tests.
    // It can be cleared with these method to it easier to compare `underlying`
    // to an expected value.

    def clearFinalDependenciesCache: Resolution =
      underlying.copy(finalDependenciesCache = Map.empty)
    def clearCaches: Resolution =
      underlying.copy(
        projectCache = Map.empty,
        errorCache = Map.empty,
        finalDependenciesCache = Map.empty
      )
    def clearFilter: Resolution =
      underlying.copy(filter = None)
  }

  object Profile {
    type Activation = core.Activation
    object Activation {
      def apply(properties: Seq[(String, Option[String])] = Nil): Activation =
        core.Activation(properties, coursier.core.Activation.Os.empty, None)
    }

    def apply(
      id: String,
      activeByDefault: Option[Boolean] = None,
      activation: Activation = Activation(),
      dependencies: Seq[(String, Dependency)] = Nil,
      dependencyManagement: Seq[(String, Dependency)] = Nil,
      properties: Map[String, String] = Map.empty
    ) =
      core.Profile(
        id,
        activeByDefault,
        activation,
        dependencies,
        dependencyManagement,
        properties
      )
  }

  object Project {
    def apply(
      module: Module,
      version: String,
      dependencies: Seq[(String, Dependency)] = Seq.empty,
      parent: Option[ModuleVersion] = None,
      dependencyManagement: Seq[(String, Dependency)] = Seq.empty,
      configurations: Map[String, Seq[String]] = Map.empty,
      properties: Seq[(String, String)] = Seq.empty,
      profiles: Seq[Profile] = Seq.empty,
      versions: Option[core.Versions] = None,
      snapshotVersioning: Option[core.SnapshotVersioning] = None,
      publications: Seq[(String, core.Publication)] = Nil
    ): Project =
      core.Project(
        module,
        version,
        dependencies,
        configurations,
        parent,
        dependencyManagement,
        properties,
        profiles,
        versions,
        snapshotVersioning,
        None,
        publications,
        Info.empty
      )
  }
}
