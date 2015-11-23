import scalaz.{ -\/, \/- }
import scalaz.concurrent.Task

/**
 * Pulls definitions from coursier.core, sometimes with default arguments.
 */
package object coursier {

  type Dependency = core.Dependency
  object Dependency {
    def apply(
      module: Module,
      version: String,
      // Substituted by Resolver with its own default scope (compile)
      scope: Scope = Scope.Other(""),
      attributes: Attributes = Attributes(),
      exclusions: Set[(String, String)] = Set.empty,
      optional: Boolean = false
    ): Dependency =
      core.Dependency(
        module,
        version,
        scope,
        attributes,
        exclusions,
        optional
      )
  }

  type Attributes = core.Attributes
  object Attributes {
    def apply(
      `type`: String = "jar",
      classifier: String = ""
    ): Attributes =
      core.Attributes(`type`, classifier)
  }

  type Project = core.Project
  val Project: core.Project.type = core.Project

  type Profile = core.Profile
  val Profile: core.Profile.type = core.Profile

  type Module = core.Module
  object Module {
    def apply(organization: String, name: String): Module =
      core.Module(organization, name)
  }

  type ModuleVersion = (core.Module, String)

  type Scope = core.Scope
  val Scope: core.Scope.type = core.Scope

  type CachePolicy = core.CachePolicy
  val CachePolicy: core.CachePolicy.type = core.CachePolicy

  type Repository = core.Repository
  val Repository: core.Repository.type = core.Repository

  type Resolution = core.Resolution
  object Resolution {
    val empty = apply()
    def apply(
      rootDependencies: Set[Dependency] = Set.empty,
      dependencies: Set[Dependency] = Set.empty,
      conflicts: Set[Dependency] = Set.empty,
      projectCache: Map[ModuleVersion, (Artifact.Source, Project)] = Map.empty,
      errorCache: Map[ModuleVersion, Seq[String]] = Map.empty,
      filter: Option[Dependency => Boolean] = None,
      profileActivation: Option[(String, core.Activation, Map[String, String]) => Boolean] = None
    ): Resolution =
      core.Resolution(
        rootDependencies,
        dependencies,
        conflicts,
        projectCache,
        errorCache,
        filter,
        profileActivation
      )
  }

  type Artifact = core.Artifact
  val Artifact: core.Artifact.type = core.Artifact

  type ResolutionProcess = core.ResolutionProcess
  val ResolutionProcess: core.ResolutionProcess.type = core.ResolutionProcess

  implicit class ResolutionExtensions(val underlying: Resolution) extends AnyVal {

    def process: ResolutionProcess = ResolutionProcess(underlying)
  }

  implicit def fetch(
    repositories: Seq[core.Repository]
  )(implicit
    cachePolicy: CachePolicy
  ): ResolutionProcess.Fetch[Task] = {

    modVers =>
      Task.gatherUnordered(
        modVers
          .map {case (module, version) =>
            Repository.find(repositories, module, version)
              .run
              .map((module, version) -> _)
          }
      )
  }

  def fetchLocalFirst(
    repositories: Seq[core.Repository]
  )(implicit
    cachePolicy: CachePolicy
  ): ResolutionProcess.Fetch[Task] = {

    modVers =>
      Task.gatherUnordered(
        modVers
          .map {case (module, version) =>
            def attempt(cachePolicy: CachePolicy) =
              Repository.find(repositories, module, version)(cachePolicy)
                .run
                .map((module, version) -> _)

            attempt(CachePolicy.LocalOnly).flatMap {
              case v @ (_, \/-(_)) => Task.now(v)
              case (_, -\/(_)) => attempt(cachePolicy)
            }
          }
      )
  }

}
