package lmcoursier.internal

import java.io.File
import java.net.URL
import java.util.GregorianCalendar
import java.util.concurrent.ConcurrentHashMap

import coursier.{Artifact, Attributes, Dependency, Module, Project, Resolution}
import coursier.core.{Classifier, Configuration, Extension, Publication, Type}
import coursier.maven.MavenAttributes
import sbt.librarymanagement.{Artifact => _, Configuration => _, _}
import sbt.util.Logger

private[internal] object SbtUpdateReport {

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

  private val moduleId = caching[(Dependency, String, Map[String, String]), ModuleID] {
    case (dependency, version, extraProperties) =>
      sbt.librarymanagement.ModuleID(
        dependency.module.organization.value,
        dependency.module.name.value,
        version
      ).withConfigurations(
        Some(dependency.configuration.value)
      ).withExtraAttributes(
        dependency.module.attributes ++ extraProperties
      ).withExclusions(
        dependency
          .exclusions
          .toVector
          .map {
            case (org, name) =>
              sbt.librarymanagement.InclExclRule()
                .withOrganization(org.value)
                .withName(name.value)
          }
      ).withIsTransitive(
        dependency.transitive
      )
  }

  private val artifact = caching[(Module, Map[String, String], Publication, Artifact), sbt.librarymanagement.Artifact] {
    case (module, extraProperties, pub, artifact) =>
      sbt.librarymanagement.Artifact(module.name.value)
        // FIXME Get these two from publications
        .withType(pub.`type`.value)
        .withExtension(MavenAttributes.typeExtension(pub.`type`).value)
        .withClassifier(
          Some(pub.classifier)
            .filter(_.nonEmpty)
            .orElse(MavenAttributes.typeDefaultClassifierOpt(pub.`type`))
            .map(_.value)
        )
        // .withConfigurations(Vector())
        .withUrl(Some(new URL(artifact.url)))
        .withExtraAttributes(module.attributes ++ extraProperties)
  }

  private val moduleReport = caching[(Dependency, Seq[(Dependency, Project)], Project, Seq[(Publication, Artifact, Option[File])]), ModuleReport] {
    case (dependency, dependees, project, artifacts) =>

    val sbtArtifacts = artifacts.collect {
      case (pub, artifact0, Some(file)) =>
        (artifact((dependency.module, project.properties.toMap, pub, artifact0)), file)
    }
    val sbtMissingArtifacts = artifacts.collect {
      case (pub, artifact0, None) =>
        artifact((dependency.module, project.properties.toMap, pub, artifact0))
    }

    val publicationDate = project.info.publication.map { dt =>
      new GregorianCalendar(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
    }

    val callers = dependees.map {
      case (dependee, dependeeProj) =>
        Caller(
          moduleId((dependee, dependeeProj.version, dependeeProj.properties.toMap)),
          dependeeProj.configurations.keys.toVector.map(c => ConfigRef(c.value)),
          dependee.module.attributes ++ dependeeProj.properties,
          // FIXME Set better values here
          isForceDependency = false,
          isChangingDependency = false,
          isTransitiveDependency = false,
          isDirectlyForceDependency = false
        )
    }

    ModuleReport(
      moduleId((dependency, project.version, project.properties.toMap)),
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
      .withConfigurations(project.configurations.keys.toVector.map(c => ConfigRef(c.value)))
      .withLicenses(project.info.licenses.toVector)
      .withCallers(callers.toVector)
  }

  private def moduleReports(
    res: Resolution,
    classifiersOpt: Option[Seq[Classifier]],
    artifactFileOpt: (Module, String, Attributes, Artifact) => Option[File],
    log: Logger,
    keepPomArtifact: Boolean = false,
    includeSignatures: Boolean = false
  ) = {
    val depArtifacts1 = res.dependencyArtifacts(classifiersOpt)

    val depArtifacts0 =
      if (keepPomArtifact)
        depArtifacts1
      else
        depArtifacts1.filter {
          case (_, pub, _) => pub.attributes != Attributes(Type.pom, Classifier.empty)
        }

    val depArtifacts =
      if (includeSignatures) {

        val notFound = depArtifacts0.filter(!_._3.extra.contains("sig"))

        if (notFound.isEmpty)
          depArtifacts0.flatMap {
            case (dep, pub, a) =>
              val sigPub = pub.copy(
                // not too sure about those
                ext = Extension(pub.ext.value),
                `type` = Type(pub.`type`.value)
              )
              Seq((dep, pub, a)) ++
                a.extra.get("sig").toSeq.map((dep, sigPub, _))
          }
        else {
          for ((_, _, a) <- notFound)
            log.error(s"No signature found for ${a.url}")
          sys.error(s"${notFound.length} signature(s) not found")
        }
      } else
        depArtifacts0

    val groupedDepArtifacts = depArtifacts
      .groupBy(_._1)
      .mapValues(_.map { case (_, attr, a) => (attr, a) })
      .iterator
      .toMap

    val versions = res.dependencies.toVector.map { dep =>
      dep.module -> dep.version
    }.toMap

    def clean(dep: Dependency): Dependency =
      dep.copy(configuration = Configuration.empty, exclusions = Set.empty, optional = false)

    val reverseDependencies = res.reverseDependencies
      .toVector
      .map { case (k, v) =>
        clean(k) -> v.map(clean)
      }
      .groupBy(_._1)
      .mapValues(_.flatMap(_._2))
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

        moduleReport((
          dep,
          dependees,
          proj,
          artifacts.map { case (pub, a) => (pub, a, artifactFileOpt(proj.module, proj.version, pub.attributes, a)) }
        ))
    }
  }

  def apply(
    configDependencies: Map[Configuration, Seq[Dependency]],
    resolutions: Map[Configuration, Resolution],
    configs: Map[Configuration, Set[Configuration]],
    classifiersOpt: Option[Seq[Classifier]],
    artifactFileOpt: (Module, String, Attributes, Artifact) => Option[File],
    log: Logger,
    keepPomArtifact: Boolean = false,
    includeSignatures: Boolean = false
  ): UpdateReport = {

    val configReports = configs.map {
      case (config, extends0) =>
        val configDeps = extends0
          .toSeq
          .sortBy(_.value)
          .flatMap(configDependencies.getOrElse(_, Nil))
          .distinct
        val subRes = resolutions(config).subset(configDeps)

        val reports = moduleReports(
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
            val mod = moduleId((dep, proj.version, proj.properties.toMap))
            val (main, other) = reports.partition { r =>
              r.module.organization == mod.organization &&
                r.module.name == mod.name &&
                r.module.crossVersion == mod.crossVersion
            }
            main.toVector ++ other.toVector
          } else
            reports.toVector

        ConfigurationReport(
          ConfigRef(config.value),
          reports0,
          Vector()
        )
    }

    UpdateReport(
      new File("."), // dummy value
      configReports.toVector,
      UpdateStats(-1L, -1L, -1L, cached = false),
      Map.empty
    )
  }

}
