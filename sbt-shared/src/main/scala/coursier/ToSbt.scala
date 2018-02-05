package coursier

import java.io.File
import java.util.GregorianCalendar
import java.util.concurrent.ConcurrentHashMap

import sbt.librarymanagement._
import sbt.util.Logger
import coursier.maven.MavenSource

object ToSbt {

  private def caching[K, V](f: K => V): K => V = {

    val cache = new ConcurrentHashMap[K, V]

    key =>
      val previousValueOpt = Option(cache.get(key))

      previousValueOpt.getOrElse {
        val value = f(key)
        val concurrentValueOpt = Option(cache.putIfAbsent(key, value))
        concurrentValueOpt.getOrElse(value)
      }
  }

  val moduleId = caching[(Dependency, Map[String, String]), ModuleID] {
    case (dependency, extraProperties) =>
      sbt.librarymanagement.ModuleID(
        dependency.module.organization,
        dependency.module.name,
        dependency.version
      ).withConfigurations(
        Some(dependency.configuration)
      ).withExtraAttributes(
        dependency.module.attributes ++ extraProperties
      ).withExclusions(
        dependency
          .exclusions
          .toVector
          .map {
            case (org, name) =>
              sbt.librarymanagement.InclExclRule()
                .withOrganization(org)
                .withName(name)
          }
      ).withIsTransitive(
        dependency.transitive
      )
  }

  val artifact = caching[(Module, Map[String, String], Artifact), sbt.librarymanagement.Artifact] {
    case (module, extraProperties, artifact) =>
      sbt.librarymanagement.Artifact(module.name)
        // FIXME Get these two from publications
        .withType(artifact.attributes.`type`)
        .withExtension(MavenSource.typeExtension(artifact.attributes.`type`))
        .withClassifier(
          Some(artifact.attributes.classifier)
            .filter(_.nonEmpty)
            .orElse(MavenSource.typeDefaultClassifierOpt(artifact.attributes.`type`))
        )
        // .withConfigurations(Vector())
        .withUrl(Some(sbt.url(artifact.url)))
        .withExtraAttributes(module.attributes ++ extraProperties)
  }

  val moduleReport = caching[(Dependency, Seq[(Dependency, Project)], Project, Seq[(Artifact, Option[File])]), ModuleReport] {
    case (dependency, dependees, project, artifacts) =>

    val sbtArtifacts = artifacts.collect {
      case (artifact, Some(file)) =>
        (ToSbt.artifact(dependency.module, project.properties.toMap, artifact), file)
    }
    val sbtMissingArtifacts = artifacts.collect {
      case (artifact, None) =>
        ToSbt.artifact(dependency.module, project.properties.toMap, artifact)
    }

    val publicationDate = project.info.publication.map { dt =>
      new GregorianCalendar(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
    }

    val callers = dependees.map {
      case (dependee, dependeeProj) =>
        sbt.Caller(
          ToSbt.moduleId(dependee, dependeeProj.properties.toMap),
          dependeeProj.configurations.keys.toVector.map(ConfigRef(_)),
          dependee.module.attributes ++ dependeeProj.properties,
          // FIXME Set better values here
          isForceDependency = false,
          isChangingDependency = false,
          isTransitiveDependency = false,
          isDirectlyForceDependency = false
        )
    }

    sbt.ModuleReport(
      ToSbt.moduleId(dependency, project.properties.toMap),
      sbtArtifacts.toVector,
      sbtMissingArtifacts.toVector
    )
      // .withStatus(None)
      .withPublicationDate(publicationDate)
      // .withResolver(None)
      // .withArtifactResolver(None)
      // .withEvicted(false)
      // .withEvictedData(None)
      // .withEvictedReason(None)
      // .withProblem(None)
      .withHomepage(Some(project.info.homePage).filter(_.nonEmpty))
      .withExtraAttributes(dependency.module.attributes ++ project.properties)
      // .withIsDefault(None)
      // .withBranch(None)
      .withConfigurations(project.configurations.keys.toVector.map(ConfigRef(_)))
      .withLicenses(project.info.licenses.toVector)
      .withCallers(callers.toVector)
  }

  private def grouped[K, V](map: Seq[(K, V)]): Map[K, Seq[V]] =
    map.groupBy { case (k, _) => k }.map {
      case (k, l) =>
        k -> l.map { case (_, v) => v }
    }

  def moduleReports(
    res: Resolution,
    classifiersOpt: Option[Seq[String]],
    artifactFileOpt: (Module, String, Artifact) => Option[File],
    log: Logger,
    keepPomArtifact: Boolean = false,
    includeSignatures: Boolean = false
  ) = {
    val depArtifacts1 =
      classifiersOpt match {
        case None => res.dependencyArtifacts(withOptional = true)
        case Some(cl) => res.dependencyClassifiersArtifacts(cl)
      }

    val depArtifacts0 =
      if (keepPomArtifact)
        depArtifacts1
      else
        depArtifacts1.filter {
          case (_, a) => a.attributes != Attributes("pom", "")
        }

    val depArtifacts =
      if (includeSignatures) {

        val notFound = depArtifacts0.filter(!_._2.extra.contains("sig"))

        if (notFound.isEmpty)
          depArtifacts0.flatMap {
            case (dep, a) =>
              Seq(dep -> a) ++ a.extra.get("sig").toSeq.map(dep -> _)
          }
        else {
          for ((_, a) <- notFound)
            log.error(s"No signature found for ${a.url}")
          sys.error(s"${notFound.length} signature(s) not found")
        }
      } else
        depArtifacts0

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
    resolutions: Map[String, Resolution],
    configs: Map[String, Set[String]],
    classifiersOpt: Option[Seq[String]],
    artifactFileOpt: (Module, String, Artifact) => Option[File],
    log: Logger,
    keepPomArtifact: Boolean = false,
    includeSignatures: Boolean = false
  ): UpdateReport = {

    val configReports = configs.map {
      case (config, extends0) =>
        val configDeps = extends0.flatMap(configDependencies.getOrElse(_, Nil))
        val subRes = resolutions(config).subset(configDeps)

        val reports = ToSbt.moduleReports(
          subRes,
          classifiersOpt,
          artifactFileOpt,
          log,
          keepPomArtifact = keepPomArtifact,
          includeSignatures = includeSignatures
        )

        val reports0 =
          if (subRes.rootDependencies.size == 1) {
            // quick hack ensuring the module for the only root dependency
            // appears first in the update report, see https://github.com/coursier/coursier/issues/650
            val dep = subRes.rootDependencies.head
            val (_, proj) = subRes.projectCache(dep.moduleVersion)
            val mod = ToSbt.moduleId(dep, proj.properties.toMap)
            val (main, other) = reports.partition { r =>
              r.module.organization == mod.organization &&
                r.module.name == mod.name &&
                r.module.crossVersion == mod.crossVersion
            }
            main.toVector ++ other.toVector
          } else
            reports.toVector

        sbt.ConfigurationReport(
          ConfigRef(config),
          reports0,
          Vector()
        )
    }

    UpdateReport(
      null,
      configReports.toVector,
      UpdateStats(-1L, -1L, -1L, cached = false),
      Map.empty
    )
  }

}
