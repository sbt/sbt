import scalaz.EitherT
import scalaz.concurrent.Task

package object coursier {

  type Dependency = core.Dependency
  object Dependency {
    def apply(module: Module,
              scope: Scope = Scope.Other(""), // Subsituted by Resolver with its own default scope (compile)
              `type`: String = "jar",
              classifier: String = "",
              exclusions: Set[(String, String)] = Set.empty,
              optional: Boolean = false): Dependency =
      core.Dependency(module, scope, `type`, classifier, exclusions, optional)
  }

  type Project = core.Project
  object Project {
    def apply(module: Module,
              dependencies: Seq[Dependency] = Seq.empty,
              parent: Option[Module] = None,
              dependencyManagement: Seq[Dependency] = Seq.empty,
              properties: Map[String, String] = Map.empty,
              profiles: Seq[Profile] = Seq.empty): Project =
      core.Project(module, dependencies, parent, dependencyManagement, properties, profiles)
  }

  type Profile = core.Profile
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

  type Module = core.Module
  object Module {
    def apply(organization: String, name: String, version: String): Module =
      core.Module(organization, name, version)
  }

  type Scope = core.Scope
  val Scope: core.Scope.type = core.Scope

  type Repository = core.Repository

  def fetchFrom(repositories: Seq[Repository]): Module => EitherT[Task, List[String], (Repository, Project)] =
    core.Resolver.find(repositories, _)

  type Resolution = core.Resolver.Resolution
  object Resolution {
    val empty = apply()
    def apply(rootDependencies: Set[Dependency] = Set.empty,
              dependencies: Set[Dependency] = Set.empty,
              conflicts: Set[Dependency] = Set.empty,
              projectsCache: Map[Module, (Repository, Project)] = Map.empty,
              errors: Map[Module, Seq[String]] = Map.empty,
              filter: Option[Dependency => Boolean] = None,
              profileActivation: Option[(String, Profile.Activation, Map[String, String]) => Boolean] = None): Resolution =
      core.Resolver.Resolution(rootDependencies, dependencies, conflicts, projectsCache, errors, filter, profileActivation)
  }

  def resolve(dependencies: Set[Dependency],
              fetch: Module => EitherT[Task, List[String], (Repository, Project)],
              maxIterations: Option[Int] = Some(200),
              filter: Option[Dependency => Boolean] = None,
              profileActivation: Option[(String, Profile.Activation, Map[String, String]) => Boolean] = None): Task[Resolution] = {
    core.Resolver.resolve(dependencies, fetch, maxIterations, filter, profileActivation)
  }
}
