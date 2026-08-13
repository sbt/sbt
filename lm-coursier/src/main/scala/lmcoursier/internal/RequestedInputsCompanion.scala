package lmcoursier.internal

object RequestedInputsCompanion {
  val empty: RequestedInputs =
    RequestedInputs(
      dependencies = Vector.empty,
      repositories = Vector.empty,
      scalaVersion = None,
      maxIterations = 0,
      forceVersions = Vector.empty,
      exclusions = Vector.empty,
      strict = None
    )

  def build(scalaVersion: Option[String], params: ResolutionParams): RequestedInputs = {
    val requestedDependencies: Vector[RequestedDependency] =
      params.dependencies
        .map { case (config, dep) =>
          RequestedDependency(
            configuration = config.value,
            organization = dep.module.organization.value,
            name = dep.module.name.value,
            version = dep.versionConstraint.asString,
            variantSelector = dep.variantSelector.repr
          )
        }
        .sortBy(d => (d.configuration, d.organization, d.name, d.version))
        .toVector

    val forceVersions: Vector[RequestedForceVersion] =
      params.params.forceVersion0.toVector
        .map { case (mod, ver) => RequestedForceVersion(mod.toString, ver.asString) }
        .sortBy(_.module)

    val exclusions: Vector[RequestedExclusion] =
      params.params.exclusions.toVector
        .map { case (org, name) => RequestedExclusion(org.value, name.value) }
        .sortBy(e => (e.organization, e.name))

    RequestedInputs(
      dependencies = requestedDependencies,
      repositories = params.mainRepositories.map(_.toString).toVector,
      scalaVersion = scalaVersion,
      maxIterations = params.params.maxIterations,
      forceVersions = forceVersions,
      exclusions = exclusions,
      strict = params.strictOpt.map(_.toString)
    )
  }

  def mismatchReasons(current: RequestedInputs, locked: RequestedInputs): Vector[String] = {
    val reasons = Vector.newBuilder[String]
    if (current.dependencies != locked.dependencies) reasons += "dependencies changed"
    if (current.repositories != locked.repositories) reasons += "repositories changed"
    if (current.scalaVersion != locked.scalaVersion) reasons += "scalaVersion changed"
    if (current.maxIterations != locked.maxIterations) reasons += "maxIterations changed"
    if (current.forceVersions != locked.forceVersions) reasons += "forceVersions changed"
    if (current.exclusions != locked.exclusions) reasons += "exclusions changed"
    if (current.strict != locked.strict) reasons += "strict changed"
    reasons.result()
  }
}
