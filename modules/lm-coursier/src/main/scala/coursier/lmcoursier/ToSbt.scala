package coursier.lmcoursier

import java.io.File
import java.net.URL
import java.util.GregorianCalendar
import java.util.concurrent.ConcurrentHashMap

import coursier.{Artifact, Attributes, Dependency, Module, Project, Resolution}
import coursier.core.{Classifier, Configuration, Type}
import coursier.maven.MavenAttributes
import sbt.librarymanagement.{Artifact => _, Configuration => _, _}
import sbt.util.Logger

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
        dependency.module.organization.value,
        dependency.module.name.value,
        dependency.version
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

  val artifact = caching[(Module, Map[String, String], Attributes, Artifact), sbt.librarymanagement.Artifact] {
    case (module, extraProperties, attr, artifact) =>
      sbt.librarymanagement.Artifact(module.name.value)
        // FIXME Get these two from publications
        .withType(attr.`type`.value)
        .withExtension(MavenAttributes.typeExtension(attr.`type`).value)
        .withClassifier(
          Some(attr.classifier)
            .filter(_.nonEmpty)
            .orElse(MavenAttributes.typeDefaultClassifierOpt(attr.`type`))
            .map(_.value)
        )
        // .withConfigurations(Vector())
        .withUrl(Some(new URL(artifact.url)))
        .withExtraAttributes(module.attributes ++ extraProperties)
  }

  val moduleReport = caching[(Dependency, Seq[(Dependency, Project)], Project, Seq[(Attributes, Artifact, Option[File])]), ModuleReport] {
    case (dependency, dependees, project, artifacts) =>

    val sbtArtifacts = artifacts.collect {
      case (attr, artifact, Some(file)) =>
        (ToSbt.artifact(dependency.module, project.properties.toMap, attr, artifact), file)
    }
    val sbtMissingArtifacts = artifacts.collect {
      case (attr, artifact, None) =>
        ToSbt.artifact(dependency.module, project.properties.toMap, attr, artifact)
    }

    val publicationDate = project.info.publication.map { dt =>
      new GregorianCalendar(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
    }

    val callers = dependees.map {
      case (dependee, dependeeProj) =>
        Caller(
          ToSbt.moduleId(dependee, dependeeProj.properties.toMap),
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
      .withConfigurations(project.configurations.keys.toVector.map(c => ConfigRef(c.value)))
      .withLicenses(project.info.licenses.toVector)
      .withCallers(callers.toVector)
  }

  def moduleReports(
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
          case (_, attr, _) => attr != Attributes(Type.pom, Classifier.empty)
        }

    val depArtifacts =
      if (includeSignatures) {

        val notFound = depArtifacts0.filter(!_._3.extra.contains("sig"))

        if (notFound.isEmpty)
          depArtifacts0.flatMap {
            case (dep, attr, a) =>
              Seq((dep, attr, a)) ++
                // not too sure about the attributes here
                a.extra.get("sig").toSeq.map((dep, Attributes(Type(s"${attr.`type`.value}.asc"), attr.classifier), _))
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

        ToSbt.moduleReport(
          dep,
          dependees,
          proj,
          artifacts.map { case (attr, a) => (attr, a, artifactFileOpt(proj.module, proj.version, attr, a)) }
        )
    }
  }

  def updateReport(
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

        ConfigurationReport(
          ConfigRef(config.value),
          reports0,
          Vector()
        )
    }

    UpdateReport(
      new File("."),
      configReports.toVector,
      UpdateStats(-1L, -1L, -1L, cached = false),
      Map.empty
    )
  }

}
