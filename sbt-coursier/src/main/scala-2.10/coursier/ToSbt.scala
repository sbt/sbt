package coursier

import java.util.GregorianCalendar

import coursier.maven.MavenSource

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
      // FIXME Get these two from publications
      artifact.attributes.`type`,
      MavenSource.typeExtension(artifact.attributes.`type`),
      Some(artifact.attributes.classifier)
        .filter(_.nonEmpty)
        .orElse(MavenSource.typeDefaultClassifierOpt(artifact.attributes.`type`)),
      Nil,
      Some(url(artifact.url)),
      Map.empty
    )

  def moduleReport(
    dependency: Dependency,
    dependees: Seq[(Dependency, Project)],
    project: Project,
    artifacts: Seq[(Artifact, Option[File])]
  ): sbt.ModuleReport = {

    val sbtArtifacts = artifacts.collect {
      case (artifact, Some(file)) =>
        (ToSbt.artifact(dependency.module, artifact), file)
    }
    val sbtMissingArtifacts = artifacts.collect {
      case (artifact, None) =>
        ToSbt.artifact(dependency.module, artifact)
    }

    val publicationDate = project.info.publication.map { dt =>
      new GregorianCalendar(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second).getTime
    }

    val callers = dependees.map {
      case (dependee, dependeeProj) =>
        new Caller(
          ToSbt.moduleId(dependee),
          dependeeProj.configurations.keys.toVector,
          dependee.module.attributes ++ dependeeProj.properties,
          // FIXME Set better values here
          isForceDependency = false,
          isChangingDependency = false,
          isTransitiveDependency = false,
          isDirectlyForceDependency = false
        )
    }

    new sbt.ModuleReport(
      module = ToSbt.moduleId(dependency),
      artifacts = sbtArtifacts,
      missingArtifacts = sbtMissingArtifacts,
      status = None,
      publicationDate = publicationDate,
      resolver = None,
      artifactResolver = None,
      evicted = false,
      evictedData = None,
      evictedReason = None,
      problem = None,
      homepage = Some(project.info.homePage).filter(_.nonEmpty),
      extraAttributes = dependency.module.attributes ++ project.properties,
      isDefault = None,
      branch = None,
      configurations = project.configurations.keys.toVector,
      licenses = project.info.licenses,
      callers = callers
    )
  }

  private def grouped[K, V](map: Seq[(K, V)]): Map[K, Seq[V]] =
    map.groupBy { case (k, _) => k }.map {
      case (k, l) =>
        k -> l.map { case (_, v) => v }
    }

  def moduleReports(
    res: Resolution,
    classifiersOpt: Option[Seq[String]],
    artifactFileOpt: (Module, String, Artifact) => Option[File]
  ) = {
    val depArtifacts =
      classifiersOpt match {
        case None => res.dependencyArtifacts
        case Some(cl) => res.dependencyClassifiersArtifacts(cl)
      }

    val groupedDepArtifacts = grouped(depArtifacts)

    val versions = res.dependencies.toVector.map { dep =>
      dep.module -> dep.version
    }.toMap

    def clean(dep: Dependency): Dependency =
      dep.copy(configuration = "", exclusions = Set.empty, optional = false)

    val reverseDependencies = res.reverseDependencies
      .toVector
      .map { case (k, v) =>
        clean(k) -> v.map(clean)
      }
      .groupBy { case (k, v) => k }
      .mapValues { v =>
        v.flatMap {
          case (_, l) => l
        }
      }
      .toVector
      .toMap

    groupedDepArtifacts.map {
      case (dep, artifacts) =>
        val (_, proj) = res.projectCache(dep.moduleVersion)

        // FIXME Likely flaky...
        val dependees = reverseDependencies
          .getOrElse(clean(dep.copy(version = "")), Vector.empty)
          .map { dependee0 =>
            val version = versions(dependee0.module)
            val dependee = dependee0.copy(version = version)
            val (_, dependeeProj) = res.projectCache(dependee.moduleVersion)
            (dependee, dependeeProj)
          }

        ToSbt.moduleReport(
          dep,
          dependees,
          proj,
          artifacts.map(a => a -> artifactFileOpt(proj.module, proj.version, a))
        )
    }
  }

  def updateReport(
    configDependencies: Map[String, Seq[Dependency]],
    resolution: Resolution,
    configs: Map[String, Set[String]],
    classifiersOpt: Option[Seq[String]],
    artifactFileOpt: (Module, String, Artifact) => Option[File]
  ): sbt.UpdateReport = {

    val configReports = configs.map {
      case (config, extends0) =>
        val configDeps = extends0.flatMap(configDependencies.getOrElse(_, Nil))
        val subRes = resolution.subset(configDeps)

        val reports = ToSbt.moduleReports(subRes, classifiersOpt, artifactFileOpt)

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
