package coursier

import sbt._

object ToSbt {

  def moduleId(dependency: Dependency): sbt.ModuleID =
    sbt.ModuleID(
      dependency.module.organization,
      dependency.module.name,
      dependency.version,
      configurations = Some(dependency.configuration),
      extraAttributes = dependency.module.attributes
    )

  def artifact(module: Module, artifact: Artifact): sbt.Artifact =
    sbt.Artifact(
      module.name,
      artifact.attributes.`type`,
      "jar",
      Some(artifact.attributes.classifier).filter(_.nonEmpty),
      Nil,
      Some(url(artifact.url)),
      Map.empty
    )

  def moduleReport(dependency: Dependency, artifacts: Seq[(Artifact, Option[File])]): sbt.ModuleReport =
    new sbt.ModuleReport(
      ToSbt.moduleId(dependency),
      artifacts.collect {
        case (artifact, Some(file)) =>
          (ToSbt.artifact(dependency.module, artifact), file)
      },
      artifacts.collect {
        case (artifact, None) =>
          ToSbt.artifact(dependency.module, artifact)
      },
      None,
      None,
      None,
      None,
      false,
      None,
      None,
      None,
      None,
      Map.empty,
      None,
      None,
      Nil,
      Nil,
      Nil
    )

  private def grouped[K, V](map: Seq[(K, V)]): Map[K, Seq[V]] =
    map.groupBy { case (k, _) => k }.map {
      case (k, l) =>
        k -> l.map { case (_, v) => v }
    }

  def moduleReports(
    res: Resolution,
    classifiersOpt: Option[Seq[String]],
    artifactFileOpt: Artifact => Option[File]
  ) = {
    val depArtifacts =
      classifiersOpt match {
        case None => res.dependencyArtifacts
        case Some(cl) => res.dependencyClassifiersArtifacts(cl)
      }

    val groupedDepArtifacts = grouped(depArtifacts)

    groupedDepArtifacts.map {
      case (dep, artifacts) =>
        ToSbt.moduleReport(dep, artifacts.map(a => a -> artifactFileOpt(a)))
    }
  }

  def updateReport(
    configDependencies: Map[String, Seq[Dependency]],
    resolution: Resolution,
    configs: Map[String, Set[String]],
    classifiersOpt: Option[Seq[String]],
    artifactFileOpt: Artifact => Option[File]
  ): sbt.UpdateReport = {

    val configReports = configs.map {
      case (config, extends0) =>
        val configDeps = extends0.flatMap(configDependencies.getOrElse(_, Nil))
        val partialRes = resolution.part(configDeps)

        val reports = ToSbt.moduleReports(partialRes, classifiersOpt, artifactFileOpt)

        new ConfigurationReport(
          config,
          reports.toVector,
          Nil,
          Nil
        )
    }

    new UpdateReport(
      null,
      configReports.toVector,
      new UpdateStats(-1L, -1L, -1L, cached = false),
      Map.empty
    )
  }

}
