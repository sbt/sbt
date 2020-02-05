package lmcoursier.internal

import java.io.File
import java.net.URL
import java.util.GregorianCalendar
import java.util.concurrent.ConcurrentHashMap

import coursier.{Attributes, Dependency, Module, Project, Resolution}
import coursier.core.{Classifier, Configuration, Extension, Publication, Type}
import coursier.maven.MavenAttributes
import coursier.util.Artifact
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

  private def infoProperties(project: Project): Seq[(String, String)] =
    project.properties.filter(_._1.startsWith("info."))

  private val moduleId = caching[(Dependency, String, Map[String, String]), ModuleID] {
    case (dependency, version, extraProperties) =>
      val mod = sbt.librarymanagement.ModuleID(
        dependency.module.organization.value,
        dependency.module.name.value,
        version
      )
      mod
        .withConfigurations(
          Some(dependency.configuration.value)
            .filter(_.nonEmpty) // ???
        )
        .withExtraAttributes(dependency.module.attributes ++ extraProperties)
        .withExclusions(
          dependency
            .exclusions
            .toVector
            .map {
              case (org, name) =>
                sbt.librarymanagement.InclExclRule()
                  .withOrganization(org.value)
                  .withName(name.value)
            }
        )
        .withIsTransitive(dependency.transitive)
  }

  private val artifact = caching[(Module, Map[String, String], Publication, Artifact), sbt.librarymanagement.Artifact] {
    case (module, extraProperties, pub, artifact) =>
      sbt.librarymanagement.Artifact(pub.name)
        .withType(pub.`type`.value)
        .withExtension(pub.ext.value)
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

  private val moduleReport = caching[(Dependency, Seq[(Dependency, ProjectInfo)], Project, Seq[(Publication, Artifact, Option[File])]), ModuleReport] {
    case (dependency, dependees, project, artifacts) =>

    val sbtArtifacts = artifacts.collect {
      case (pub, artifact0, Some(file)) =>
        (artifact((dependency.module, infoProperties(project).toMap, pub, artifact0)), file)
    }
    val sbtMissingArtifacts = artifacts.collect {
      case (pub, artifact0, None) =>
        artifact((dependency.module, infoProperties(project).toMap, pub, artifact0))
    }

    val publicationDate = project.info.publication.map { dt =>
      new GregorianCalendar(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
    }

    val callers = dependees.distinct.map {
      case (dependee, dependeeProj) =>
        Caller(
          moduleId((dependee, dependeeProj.version, Map.empty)),
          // FIXME Shouldn't we only keep the configurations pulling dependency?
          dependeeProj.configs,
          dependee.module.attributes ++ dependeeProj.properties,
          // FIXME Set better values here
          isForceDependency = false,
          isChangingDependency = false,
          isTransitiveDependency = dependency.transitive,
          isDirectlyForceDependency = false
        )
    }

    val rep = ModuleReport(
      moduleId((dependency, project.version, infoProperties(project).toMap)),
      sbtArtifacts.toVector,
      sbtMissingArtifacts.toVector
    )

    rep
      // .withStatus(None)
      .withPublicationDate(publicationDate)
      // .withResolver(None)
      // .withArtifactResolver(None)
      // .withEvicted(false)
      // .withEvictedData(None)
      // .withEvictedReason(None)
      // .withProblem(None)
      .withHomepage(Some(project.info.homePage).filter(_.nonEmpty))
      .withLicenses(project.info.licenses.toVector)
      .withExtraAttributes(dependency.module.attributes ++ infoProperties(project))
      // .withIsDefault(None)
      // .withBranch(None)
      .withConfigurations(project.configurations.keys.toVector.map(c => ConfigRef(c.value)))
      .withLicenses(project.info.licenses.toVector)
      .withCallers(callers.toVector)
  }

  private def moduleReports(
    thisModule: (Module, String),
    config: Configuration,
    res: Resolution,
    interProjectDependencies: Seq[Project],
    classifiersOpt: Option[Seq[Classifier]],
    artifactFileOpt: (Module, String, Attributes, Artifact) => Option[File],
    log: Logger,
    keepPomArtifact: Boolean = false,
    includeSignatures: Boolean = false,
    classpathOrder: Boolean,
  ): Vector[ModuleReport] = {
    val depArtifacts1 = res.dependencyArtifacts(classifiersOpt, classpathOrder)

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
              val sigPub = pub
                // not too sure about those
                .withExt(Extension(pub.ext.value))
                .withType(Type(pub.`type`.value))
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

    val groupedDepArtifacts = {
      val m = depArtifacts.groupBy(_._1)
      val fromLib = depArtifacts.map(_._1).distinct.map { dep =>
        dep -> m.getOrElse(dep, Nil).map { case (_, pub, a) => (pub, a) }
      }
      val fromInterProj = interProjectDependencies
        .filter(p => p.module != thisModule._1)
        .map(p => Dependency(p.module, p.version) -> Nil)
      fromLib ++ fromInterProj
    }

    val versions = (Vector(Dependency(thisModule._1, thisModule._2)) ++ res.dependencies.toVector ++ res.rootDependencies.toVector)
      .map { dep =>
        dep.module -> dep.version
      }.toMap

    def clean(dep: Dependency): Dependency =
      dep
        .withConfiguration(Configuration.empty)
        .withExclusions(Set.empty)
        .withOptional(false)

    def lookupProject(mv: coursier.core.Resolution.ModuleVersion): Option[Project] =
      res.projectCache.get(mv) match {
        case Some((_, p)) => Some(p)
        case _ =>
          interProjectDependencies.find( p =>
            mv == (p.module, p.version)
          )
      }

    val m = Dependency(thisModule._1, "")
    val directReverseDependencies = res.rootDependencies.toSet.map(clean).map(_.withVersion(""))
      .map(
        dep => dep -> Vector(m)
      )
      .toMap

    val reverseDependencies = res.reverseDependencies
      .toVector
      .map { case (k, v) =>
        clean(k) -> v.map(clean)
      }
      .groupBy(_._1)
      .mapValues(_.flatMap(_._2))
      .toVector
      .toMap ++ directReverseDependencies

    groupedDepArtifacts.toVector.map {
      case (dep, artifacts) =>
        val proj = lookupProject(dep.moduleVersion).get

        // FIXME Likely flaky...
        val dependees = reverseDependencies
          .getOrElse(clean(dep.withVersion("")), Vector.empty)
          .flatMap { dependee0 =>
            val version = versions(dependee0.module)
            val dependee = dependee0.withVersion(version)
            lookupProject(dependee.moduleVersion) match {
              case Some(dependeeProj) =>
                Vector((dependee, ProjectInfo(
                  dependeeProj.version,
                  dependeeProj.configurations.keys.toVector.map(c => ConfigRef(c.value)),
                  dependeeProj.properties)))
              case _ =>
                Vector.empty
            }
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
    thisModule: (Module, String),
    configDependencies: Map[Configuration, Seq[Dependency]],
    resolutions: Map[Configuration, Resolution],
    interProjectDependencies: Vector[Project],
    configs: Map[Configuration, Set[Configuration]],
    classifiersOpt: Option[Seq[Classifier]],
    artifactFileOpt: (Module, String, Attributes, Artifact) => Option[File],
    log: Logger,
    keepPomArtifact: Boolean = false,
    includeSignatures: Boolean = false,
    classpathOrder: Boolean,
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
          thisModule,
          config,
          subRes,
          interProjectDependencies,
          classifiersOpt,
          artifactFileOpt,
          log,
          keepPomArtifact = keepPomArtifact,
          includeSignatures = includeSignatures,
          classpathOrder = classpathOrder,
        )

        val reports0 =
          if (subRes.rootDependencies.size == 1) {
            // quick hack ensuring the module for the only root dependency
            // appears first in the update report, see https://github.com/coursier/coursier/issues/650
            val dep = subRes.rootDependencies.head
            val (_, proj) = subRes.projectCache(dep.moduleVersion)
            val mod = moduleId((dep, proj.version, infoProperties(proj).toMap))
            val (main, other) = reports.partition { r =>
              r.module.organization == mod.organization &&
                r.module.name == mod.name &&
                r.module.crossVersion == mod.crossVersion
            }
            main.toVector ++ other.toVector
          } else
            reports.toVector

        val mainReportDetails = reports0.map { rep =>
          OrganizationArtifactReport(rep.module.organization, rep.module.name, Vector(rep))
        }

        val evicted = coursier.graph.Conflict(subRes).flatMap { c =>
          // FIXME The project for c.wantedVersion is possibly not around (it's likely it was just not fetched)
          val projOpt = subRes.projectCache.get((c.module, c.wantedVersion))
            .orElse(subRes.projectCache.get((c.module, c.version)))
          projOpt.toSeq.map {
            case (_, proj) =>
              // likely misses some details (transitive, exclusions, …)
              val dep = Dependency(c.module, c.wantedVersion)
              val dependee = Dependency(c.dependeeModule, c.dependeeVersion)
              val dependeeProj = subRes.projectCache
                .get((c.dependeeModule, c.dependeeVersion)) match {
                  case Some((_, p)) =>
                    ProjectInfo(p.version, p.configurations.keys.toVector.map(c => ConfigRef(c.value)), p.properties)
                  case _ =>
                    // should not happen
                    ProjectInfo(c.dependeeVersion, Vector.empty, Vector.empty)
                }
              val rep = moduleReport((dep, Seq((dependee, dependeeProj)), proj.withVersion(c.wantedVersion), Nil))
                .withEvicted(true)
                .withEvictedData(Some("version selection")) // ??? put latest-revision like sbt/ivy here?
              OrganizationArtifactReport(c.module.organization.value, c.module.name.value, Vector(rep))
          }
        }

        val details = (mainReportDetails ++ evicted)
          .groupBy(r => (r.organization, r.name))
          .toVector // order?
          .map {
            case ((org, name), l) =>
              val modules = l.flatMap(_.modules)
              OrganizationArtifactReport(org, name, modules)
          }

        ConfigurationReport(
          ConfigRef(config.value),
          reports0,
          details
        )
    }

    UpdateReport(
      new File("."), // dummy value
      configReports.toVector,
      UpdateStats(-1L, -1L, -1L, cached = false),
      Map.empty
    )
  }

  private case class ProjectInfo(version: String, configs: Vector[ConfigRef], properties: Seq[(String, String)])
}
