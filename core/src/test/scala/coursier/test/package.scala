package coursier

package object test {

  implicit class DependencyOps(val underlying: Dependency) extends AnyVal {
    def withCompileScope: Dependency = underlying.copy(scope = Scope.Compile)
  }

  object Profile {
    type Activation = core.Activation
    object Activation {
      def apply(properties: Seq[(String, Option[String])] = Nil): Activation =
        core.Activation(properties)
    }

    def apply(id: String,
              activeByDefault: Option[Boolean] = None,
              activation: Activation = Activation(),
              dependencies: Seq[Dependency] = Nil,
              dependencyManagement: Seq[Dependency] = Nil,
              properties: Map[String, String] = Map.empty) =
      core.Profile(id, activeByDefault, activation, dependencies, dependencyManagement, properties)
  }

  object Project {
    def apply(module: Module,
              version: String,
              dependencies: Seq[Dependency] = Seq.empty,
              parent: Option[ModuleVersion] = None,
              dependencyManagement: Seq[Dependency] = Seq.empty,
              properties: Map[String, String] = Map.empty,
              profiles: Seq[Profile] = Seq.empty,
              versions: Option[core.Versions] = None): Project =
      core.Project(module, version, dependencies, parent, dependencyManagement, properties, profiles, versions)
  }
}
