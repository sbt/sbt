
/**
 * Mainly pulls definitions from coursier.core, sometimes with default arguments.
 */
package object coursier {

  type Dependency = core.Dependency
  object Dependency {
    def apply(
      module: Module,
      version: String,
      // Substituted by Resolver with its own default configuration (compile)
      configuration: String = "",
      attributes: Attributes = Attributes(),
      exclusions: Set[(String, String)] = Set.empty,
      optional: Boolean = false,
      transitive: Boolean = true
    ): Dependency =
      core.Dependency(
        module,
        version,
        configuration,
        exclusions,
        attributes,
        optional,
        transitive
      )
  }

  type Attributes = core.Attributes
  object Attributes {
    def apply(
      `type`: String = "",
      classifier: String = ""
    ): Attributes =
      core.Attributes(`type`, classifier)
  }

  type Project = core.Project
  val Project = core.Project

  type Info = core.Info
  val Info = core.Info

  type Profile = core.Profile
  val Profile = core.Profile

  type Module = core.Module
  object Module {
    def apply(organization: String, name: String, attributes: Map[String, String] = Map.empty): Module =
      core.Module(organization, name, attributes)
  }

  type ModuleVersion = (core.Module, String)


  type Repository = core.Repository
  val Repository = core.Repository

  type MavenRepository = maven.MavenRepository
  val MavenRepository = maven.MavenRepository

  type Resolution = core.Resolution
  object Resolution {
    val empty = apply()
    def apply(
      rootDependencies: Set[Dependency] = Set.empty,
      dependencies: Set[Dependency] = Set.empty,
      forceVersions: Map[Module, String] = Map.empty,
      conflicts: Set[Dependency] = Set.empty,
      projectCache: Map[ModuleVersion, (Artifact.Source, Project)] = Map.empty,
      errorCache: Map[ModuleVersion, Seq[String]] = Map.empty,
      finalDependencies: Map[Dependency, Seq[Dependency]] = Map.empty,
      filter: Option[Dependency => Boolean] = None,
      profileActivation: Option[(String, core.Activation, Map[String, String]) => Boolean] = None
    ): Resolution =
      core.Resolution(
        rootDependencies,
        dependencies,
        forceVersions,
        conflicts,
        projectCache,
        errorCache,
        finalDependencies,
        filter,
        profileActivation
      )
  }

  type Artifact = core.Artifact
  val Artifact = core.Artifact

  type ResolutionProcess = core.ResolutionProcess
  val ResolutionProcess = core.ResolutionProcess

  implicit class ResolutionExtensions(val underlying: Resolution) extends AnyVal {

    def process: ResolutionProcess = ResolutionProcess(underlying)
  }

}
