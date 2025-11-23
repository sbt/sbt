package lmcoursier.internal

import java.io.File
import java.util.{ Collections, GregorianCalendar, WeakHashMap }
import coursier.cache.CacheUrl
import coursier.{ Attributes, Dependency, Module, Project, Resolution }
import coursier.core.{
  Classifier,
  Configuration,
  Extension,
  Info,
  MinimizedExclusions,
  Publication,
  Type,
  VariantPublication
}
import coursier.maven.MavenAttributes
import coursier.util.Artifact
import sbt.librarymanagement.{ Artifact as _, Configuration as _, * }
import sbt.util.Logger
import scala.annotation.nowarn

import scala.annotation.tailrec

private[internal] object SbtUpdateReport {

  private def caching[K, V](f: K => V): K => V = {

    val cache = Collections.synchronizedMap(new WeakHashMap[K, V])

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

  @nowarn
  private val moduleId = caching[(Dependency, String, Map[String, String]), ModuleID] {
    (dependency, version, extraProperties) =>
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
          dependency.minimizedExclusions.toVector
            .map { (org, name) =>
              sbt.librarymanagement
                .InclExclRule()
                .withOrganization(org.value)
                .withName(name.value)
            }
        )
        .withIsTransitive(dependency.transitive)
  }

  private val artifact = caching[
    (Module, Map[String, String], Publication, Artifact, Seq[ClassLoader]),
    sbt.librarymanagement.Artifact
  ] { (module, extraProperties, pub, artifact, classLoaders) =>
    sbt.librarymanagement
      .Artifact(pub.name)
      .withType(pub.`type`.value)
      .withExtension(pub.ext.value)
      .withClassifier(
        Some(pub.classifier)
          .filter(_.nonEmpty)
          .orElse(MavenAttributes.typeDefaultClassifierOpt(pub.`type`))
          .map(_.value)
      )
      .withUrl(Some(CacheUrl.url(artifact.url, classLoaders).toURI))
      .withExtraAttributes(module.attributes ++ extraProperties)
  }

  @nowarn
  private val moduleReport = caching[
    (
        Dependency,
        Seq[(Dependency, ProjectInfo)],
        Project,
        Seq[(Publication, Artifact, Option[File])],
        Seq[ClassLoader]
    ),
    ModuleReport
  ] { (dependency, dependees, project, artifacts, classLoaders) =>
    val sbtArtifacts = artifacts.collect { case (pub, artifact0, Some(file)) =>
      (
        artifact((dependency.module, infoProperties(project).toMap, pub, artifact0, classLoaders)),
        file
      )
    }
    val sbtMissingArtifacts = artifacts.collect { case (pub, artifact0, None) =>
      artifact((dependency.module, infoProperties(project).toMap, pub, artifact0, classLoaders))
    }

    val explicitArts = artifacts
      .collect {
        case (pub, _, _) if pub.classifier.nonEmpty =>
          sbt.librarymanagement
            .Artifact(pub.name)
            .withType(pub.`type`.value)
            .withExtension(pub.ext.value)
            .withClassifier(Some(pub.classifier.value))
      }
      .distinct
      .toVector

    val publicationDate = project.info.publication.map { dt =>
      new GregorianCalendar(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
    }

    val callers = dependees.distinct.map { (dependee, dependeeProj) =>
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

    val modId = moduleId((dependency, project.version, infoProperties(project).toMap))
    val modIdWithArts =
      if explicitArts.nonEmpty then modId.withExplicitArtifacts(explicitArts)
      else modId

    val rep = ModuleReport(
      modIdWithArts,
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

  @nowarn
  private def moduleReports(
      thisModule: (Module, String),
      res: Resolution,
      interProjectDependencies: Seq[Project],
      classifiersOpt: Option[Seq[Classifier]],
      artifactFileOpt: (Module, String, Attributes, Artifact) => Option[File],
      fullArtifactsOpt: Option[
        Map[(Dependency, Either[VariantPublication, Publication], Artifact), Option[File]]
      ],
      log: Logger,
      includeSignatures: Boolean,
      classpathOrder: Boolean,
      missingOk: Boolean,
      classLoaders: Seq[ClassLoader]
  ): Vector[ModuleReport] = {

    val deps = classifiersOpt match {
      case Some(classifiers) =>
        res.dependencyArtifacts(Some(classifiers), classpathOrder)
      case None =>
        res.dependencyArtifacts(None, classpathOrder)
    }

    val depArtifacts1 = fullArtifactsOpt match {
      case Some(map) =>
        deps.map { (d, p, a) =>
          val d0 = d.withAttributes(d.attributes.withClassifier(p.classifier))
          val a0 = if (missingOk) a.withOptional(true) else a
          val f = map.get((d0, Right(p), a0)).flatten
          (d, p, a0, f) // not d0
        }
      case None =>
        deps.map { (d, p, a) =>
          (d, p, a, None)
        }
    }

    val depArtifacts0 = depArtifacts1.filter { case (_, pub, _, _) =>
      pub.attributes != Attributes(Type.pom, Classifier.empty)
    }

    val depArtifacts =
      if (includeSignatures) {

        val notFound = depArtifacts0.filter(!_._3.extra.contains("sig"))

        if (notFound.isEmpty)
          depArtifacts0.flatMap { (dep, pub, a, f) =>
            val sigPub = pub
              // not too sure about those
              .withExt(Extension(pub.ext.value))
              .withType(Type(pub.`type`.value))
            Seq((dep, pub, a, f)) ++
              a.extra.get("sig").toSeq.map((dep, sigPub, _, None))
          }
        else {
          for ((_, _, a, _) <- notFound)
            log.error(s"No signature found for ${a.url}")
          sys.error(s"${notFound.length} signature(s) not found")
        }
      } else
        depArtifacts0

    val groupedDepArtifacts = {
      val m = depArtifacts.groupBy(_._1)
      val fromLib = depArtifacts.map(_._1).distinct.map { dep =>
        dep -> m.getOrElse(dep, Nil).map { case (_, pub, a, f) => (pub, a, f) }
      }
      val fromInterProj = interProjectDependencies
        .withFilter(p => p.module != thisModule._1)
        .map(p => Dependency(p.module, p.version) -> Nil)
      fromLib ++ fromInterProj
    }

    val versions = (Vector(
      Dependency(thisModule._1, thisModule._2)
    ) ++ res.dependencies.toVector ++ res.rootDependencies.toVector).map { dep =>
      dep.module -> dep.version
    }.toMap

    def clean(dep: Dependency): Dependency =
      dep
        .withConfiguration(Configuration.empty)
        .withMinimizedExclusions(MinimizedExclusions.zero)
        .withOptional(false)

    def lookupProject(mv: coursier.core.Resolution.ModuleVersion): Option[Project] =
      res.projectCache.get(mv) match {
        case Some((_, p)) => Some(p)
        case _ =>
          interProjectDependencies.find(p => mv == (p.module, p.version))
      }

    /**
     * Assemble the project info, resolving inherited fields. Only implements resolving
     * the fields that are relevant for moduleReport
     *
     * @see https://maven.apache.org/pom.html#Inheritance
     * @see https://maven.apache.org/ref/3-LATEST/maven-model-builder/index.html#Inheritance_Assembly
     */
    def assemble(project: Project): Project = {
      @tailrec
      def licenseInfo(project: Project): Seq[Info.License] = {
        if (project.info.licenseInfo.nonEmpty || project.parent.isEmpty)
          project.info.licenseInfo
        else
          licenseInfo(lookupProject(project.parent.get).get)
      }
      project.withInfo(
        project.info.withLicenseInfo(licenseInfo(project))
      )
    }

    val m = Dependency(thisModule._1, "")
    val directReverseDependencies = res.rootDependencies.toSet
      .map(clean)
      .map(_.withVersion(""))
      .map(dep => dep -> Vector(m))
      .toMap

    val reverseDependencies = {
      val transitiveReverseDependencies = res.reverseDependencies.toVector
        .map { (k, v) => clean(k) -> v.map(clean) }
        .groupMapReduce(_._1)((_, deps) => deps)(_ ++ _)

      (transitiveReverseDependencies.toVector ++ directReverseDependencies.toVector)
        .groupMapReduce(_._1)((_, deps) => deps)(_ ++ _)
    }

    groupedDepArtifacts.toVector.map { (dep, artifacts) =>
      val proj = lookupProject(dep.moduleVersion).get
      val assembledProject = assemble(proj)

      // FIXME Likely flaky...
      val dependees = reverseDependencies
        .getOrElse(clean(dep.withVersion("")), Vector.empty)
        .flatMap { dependee0 =>
          val version = versions(dependee0.module)
          val dependee = dependee0.withVersion(version)
          lookupProject(dependee.moduleVersion) match {
            case Some(dependeeProj) =>
              Vector(
                (
                  dependee,
                  ProjectInfo(
                    dependeeProj.version,
                    dependeeProj.configurations.keys.toVector.map(c => ConfigRef(c.value)),
                    dependeeProj.properties
                  )
                )
              )
            case _ =>
              Vector.empty
          }
        }
      val filesOpt = artifacts.map { (pub, a, fileOpt) =>
        val fileOpt0 = fileOpt.orElse {
          if (fullArtifactsOpt.isEmpty)
            artifactFileOpt(proj.module, proj.version, pub.attributes, a)
          else None
        }
        (pub, a, fileOpt0)
      }
      moduleReport(
        (
          dep,
          dependees,
          assembledProject,
          filesOpt,
          classLoaders,
        )
      )
    }
  }

  @nowarn
  def apply(
      thisModule: (Module, String),
      configDependencies: Map[Configuration, Seq[Dependency]],
      resolutions: Seq[(Configuration, Resolution)],
      interProjectDependencies: Vector[Project],
      classifiersOpt: Option[Seq[Classifier]],
      artifactFileOpt: (Module, String, Attributes, Artifact) => Option[File],
      fullArtifactsOpt: Option[
        Map[(Dependency, Either[VariantPublication, Publication], Artifact), Option[File]]
      ],
      log: Logger,
      includeSignatures: Boolean,
      classpathOrder: Boolean,
      missingOk: Boolean,
      forceVersions: Map[Module, String],
      classLoaders: Seq[ClassLoader],
  ): UpdateReport = {

    val configReports = resolutions.map { (config, subRes) =>
      val reports = moduleReports(
        thisModule,
        subRes,
        interProjectDependencies,
        classifiersOpt,
        artifactFileOpt,
        fullArtifactsOpt,
        log,
        includeSignatures = includeSignatures,
        classpathOrder = classpathOrder,
        missingOk = missingOk,
        classLoaders = classLoaders,
      )

      val reports0 = subRes.rootDependencies match {
        case Seq(dep) if subRes.projectCache.contains(dep.moduleVersion) =>
          // quick hack ensuring the module for the only root dependency
          // appears first in the update report, see https://github.com/coursier/coursier/issues/650
          val (_, proj) = subRes.projectCache(dep.moduleVersion)
          val mod = moduleId((dep, proj.version, infoProperties(proj).toMap))
          val (main, other) = reports.partition { r =>
            r.module.organization == mod.organization &&
            r.module.name == mod.name &&
            r.module.crossVersion == mod.crossVersion
          }
          main ++ other
        case _ => reports
      }

      val mainReportDetails = reports0.map { rep =>
        OrganizationArtifactReport(rep.module.organization, rep.module.name, Vector(rep))
      }

      def conflicts: Seq[coursier.graph.Conflict] =
        try coursier.graph.Conflict(subRes)
        catch case e: Throwable if missingOk => Nil
      val evicted = for {
        c <- conflicts
        // ideally, forceVersions should be taken into account by coursier.core.Resolution itself, when
        // it computes transitive dependencies. It only handles forced versions at a global level for now,
        // rather than handing them for each dependency (where each dependency could have its own forced
        // versions, and apply and pass them to its transitive dependencies, just like for exclusions today).
        if !forceVersions.contains(c.module)
        projOpt = subRes.projectCache
          .get((c.module, c.wantedVersion))
          .orElse(subRes.projectCache.get((c.module, c.version)))
        (_, proj) <- projOpt.toSeq
      } yield {
        val dep = Dependency(c.module, c.wantedVersion)
        val dependee = Dependency(c.dependeeModule, c.dependeeVersion)
        val dependeeProj = subRes.projectCache.get((c.dependeeModule, c.dependeeVersion)) match {
          case Some((_, p)) =>
            ProjectInfo(
              p.version,
              p.configurations.keys.toVector.map(c => ConfigRef(c.value)),
              p.properties
            )
          case None =>
            // should not happen
            ProjectInfo(c.dependeeVersion, Vector.empty, Vector.empty)
        }
        val rep = moduleReport(
          (dep, Seq((dependee, dependeeProj)), proj.withVersion(c.wantedVersion), Nil, classLoaders)
        )
          .withEvicted(true)
          .withEvictedData(Some("version selection")) // ??? put latest-revision like sbt/ivy here?
        OrganizationArtifactReport(c.module.organization.value, c.module.name.value, Vector(rep))
      }

      val details = (mainReportDetails ++ evicted)
        .groupBy(r => (r.organization, r.name))
        .toVector // order?
        .map { case ((org, name), l) =>
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
      UpdateStats(-1L, -1L, -1L, cached = false, stamp = Some(System.currentTimeMillis().toString)),
      Map.empty
    )
  }

  private case class ProjectInfo(
      version: String,
      configs: Vector[ConfigRef],
      properties: Seq[(String, String)]
  )
}
