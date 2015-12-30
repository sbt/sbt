
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
      optional: Boolean = false
    ): Dependency =
      core.Dependency(
        module,
        version,
        configuration,
        exclusions,
        attributes,
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
  val Project = core.Project

  type Profile = core.Profile
  val Profile = core.Profile

  type Module = core.Module
  object Module {
    def apply(organization: String, name: String): Module =
      core.Module(organization, name)
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
