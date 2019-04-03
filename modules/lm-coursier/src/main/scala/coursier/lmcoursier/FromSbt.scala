package coursier.lmcoursier

import coursier.ivy.IvyRepository
import coursier.ivy.IvyXml.{mappings => ivyXmlMappings}
import java.net.MalformedURLException
import coursier.cache.CacheUrl
import coursier.{Attributes, Dependency, Module}
import coursier.core._
import coursier.maven.MavenRepository
import org.apache.ivy.plugins.resolver.IBiblioResolver
import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.librarymanagement.{Configuration => _, MavenRepository => _, _}
import sbt.util.Logger
import scala.collection.JavaConverters._

object FromSbt {

  def sbtModuleIdName(
    moduleId: ModuleID,
    scalaVersion: => String,
    scalaBinaryVersion: => String,
    optionalCrossVer: Boolean = false
  ): String = {
    val name0 = moduleId.name
    val updatedName = sbtCrossVersionName(name0, moduleId.crossVersion, scalaVersion, scalaBinaryVersion)
    if (!optionalCrossVer || updatedName.length <= name0.length)
      updatedName
    else {
      val suffix = updatedName.substring(name0.length)
      if (name0.endsWith(suffix))
        name0
      else
        updatedName
    }
  }

  def sbtCrossVersionName(
    name: String,
    crossVersion: CrossVersion,
    scalaVersion: => String,
    scalaBinaryVersion: => String
  ): String =
    CrossVersion(crossVersion, scalaVersion, scalaBinaryVersion)
      .fold(name)(_(name))

  def attributes(attr: Map[String, String]): Map[String, String] =
    attr.map { case (k, v) =>
      k.stripPrefix("e:") -> v
    }.filter { case (k, _) =>
      !k.startsWith(SbtPomExtraProperties.POM_INFO_KEY_PREFIX)
    }

  def moduleVersion(
    module: ModuleID,
    scalaVersion: String,
    scalaBinaryVersion: String,
    optionalCrossVer: Boolean = false
  ): (Module, String) = {

    val fullName = sbtModuleIdName(module, scalaVersion, scalaBinaryVersion, optionalCrossVer)

    val module0 = Module(Organization(module.organization), ModuleName(fullName), FromSbt.attributes(module.extraDependencyAttributes))
    val version = module.revision

    (module0, version)
  }

  def dependencies(
    module: ModuleID,
    scalaVersion: String,
    scalaBinaryVersion: String,
    optionalCrossVer: Boolean = false
  ): Seq[(Configuration, Dependency)] = {

    // TODO Warn about unsupported properties in `module`

    val (module0, version) = moduleVersion(module, scalaVersion, scalaBinaryVersion, optionalCrossVer)

    val dep = Dependency(
      module0,
      version,
      exclusions = module.exclusions.map { rule =>
        // FIXME Other `rule` fields are ignored here
        (Organization(rule.organization), ModuleName(rule.name))
      }.toSet,
      transitive = module.isTransitive
    )

    val mapping = module.configurations.getOrElse("compile")
    val allMappings = ivyXmlMappings(mapping)

    val attributes =
      if (module.explicitArtifacts.isEmpty)
        Seq(Attributes(Type.empty, Classifier.empty))
      else
        module.explicitArtifacts.map { a =>
          Attributes(
            `type` = Type(a.`type`),
            classifier = a.classifier.fold(Classifier.empty)(Classifier(_))
          )
        }

    for {
      (from, to) <- allMappings
      attr <- attributes
    } yield from -> dep.copy(configuration = to, attributes = attr)
  }

  def fallbackDependencies(
    allDependencies: Seq[ModuleID],
    scalaVersion: String,
    scalaBinaryVersion: String
  ): Seq[FallbackDependency] =
    for {
      module <- allDependencies
      artifact <- module.explicitArtifacts
      url <- artifact.url.toSeq
    } yield {
      val (module0, version) = moduleVersion(module, scalaVersion, scalaBinaryVersion)
      FallbackDependency(module0, version, url, module.isChanging)
    }

  def sbtClassifiersProject(
    cm: GetClassifiersModule,
    scalaVersion: String,
    scalaBinaryVersion: String
  ) = {

    val p = FromSbt.project(
      cm.id,
      cm.dependencies,
      cm.configurations.map(cfg => Configuration(cfg.name) -> cfg.extendsConfigs.map(c => Configuration(c.name))).toMap,
      scalaVersion,
      scalaBinaryVersion
    )

    // for w/e reasons, the dependencies sometimes don't land in the right config above
    // this is a loose attempt at fixing that
    cm.configurations match {
      case Seq(cfg) =>
        p.copy(
          dependencies = p.dependencies.map {
            case (_, d) => (Configuration(cfg.name), d)
          }
        )
      case _ =>
        p
    }
  }

  def project(
    projectID: ModuleID,
    allDependencies: Seq[ModuleID],
    ivyConfigurations: Map[Configuration, Seq[Configuration]],
    scalaVersion: String,
    scalaBinaryVersion: String
  ): Project = {

    val deps = allDependencies.flatMap(dependencies(_, scalaVersion, scalaBinaryVersion))

    Project(
      Module(
        Organization(projectID.organization),
        ModuleName(sbtModuleIdName(projectID, scalaVersion, scalaBinaryVersion)),
        FromSbt.attributes(projectID.extraDependencyAttributes)
      ),
      projectID.revision,
      deps,
      ivyConfigurations,
      None,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      relocated = false,
      None,
      Nil,
      Info.empty
    )
  }

  private def mavenCompatibleBaseOpt(patterns: Patterns): Option[String] =
    if (patterns.isMavenCompatible) {
      val baseIvyPattern = patterns.ivyPatterns.head.takeWhile(c => c != '[' && c != '(')
      val baseArtifactPattern = patterns.ivyPatterns.head.takeWhile(c => c != '[' && c != '(')

      if (baseIvyPattern == baseArtifactPattern)
        Some(baseIvyPattern)
      else
        None
    } else
      None

  private def mavenRepositoryOpt(
    root: String,
    log: Logger
  ): Option[MavenRepository] =
    try {
      CacheUrl.url(root) // ensure root is a URL whose protocol can be handled here
      val root0 = if (root.endsWith("/")) root else root + "/"
      Some(MavenRepository(root0))
    } catch {
      case e: MalformedURLException =>
        log.warn(
          "Error parsing Maven repository base " +
          root +
          Option(e.getMessage).fold("")(" (" + _ + ")") +
          ", ignoring it"
        )

        None
    }

  def repository(
    resolver: Resolver,
    ivyProperties: Map[String, String],
    log: Logger
  ): Option[Repository] =
    resolver match {
      case r: sbt.librarymanagement.MavenRepository =>
        mavenRepositoryOpt(r.root, log)

      case r: FileRepository
        if r.patterns.ivyPatterns.lengthCompare(1) == 0 &&
          r.patterns.artifactPatterns.lengthCompare(1) == 0 =>

        val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(r.patterns)

        mavenCompatibleBaseOpt0 match {
          case None =>

            val repo = IvyRepository.parse(
              "file://" + r.patterns.artifactPatterns.head,
              metadataPatternOpt = Some("file://" + r.patterns.ivyPatterns.head),
              changing = Some(true),
              properties = ivyProperties,
              dropInfoAttributes = true
            ) match {
              case Left(err) =>
                sys.error(
                  s"Cannot parse Ivy patterns ${r.patterns.artifactPatterns.head} and ${r.patterns.ivyPatterns.head}: $err"
                )
              case Right(repo) =>
                repo
            }

            Some(repo)

          case Some(mavenCompatibleBase) =>
            mavenRepositoryOpt("file://" + mavenCompatibleBase, log)
        }

      case r: URLRepository if patternMatchGuard(r.patterns) =>
        parseMavenCompatResolver(log, ivyProperties, r.patterns)

      case raw: RawRepository if raw.name == "inter-project" => // sbt.RawRepository.equals just compares names anyway
        None

      // Pattern Match resolver-type-specific RawRepositories
      case IBiblioRepository(p) =>
        parseMavenCompatResolver(log, ivyProperties, p)

      case other =>
        log.warn(s"Unrecognized repository ${other.name}, ignoring it")
        None
    }

  private object IBiblioRepository {

    private def stringVector(v: java.util.List[_]): Vector[String] =
      Option(v).map(_.asScala.toVector).getOrElse(Vector.empty).collect {
        case s: String => s
      }

    private def patterns(resolver: IBiblioResolver): Patterns = Patterns(
      ivyPatterns = stringVector(resolver.getIvyPatterns),
      artifactPatterns = stringVector(resolver.getArtifactPatterns),
      isMavenCompatible = resolver.isM2compatible,
      descriptorOptional = !resolver.isUseMavenMetadata,
      skipConsistencyCheck = !resolver.isCheckconsistency
    )

    def unapply(r: Resolver): Option[Patterns] =
      r match {
        case raw: RawRepository =>
          raw.resolver match {
            case b: IBiblioResolver =>
              Some(patterns(b))
                .filter(patternMatchGuard)
            case _ =>
              None
          }
        case _ =>
          None
      }
  }

  private def patternMatchGuard(patterns: Patterns): Boolean =
    patterns.ivyPatterns.lengthCompare(1) == 0 &&
      patterns.artifactPatterns.lengthCompare(1) == 0

  private def parseMavenCompatResolver(
    log: Logger,
    ivyProperties: Map[String, String],
    patterns: Patterns
  ): Option[Repository] = {
    val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(patterns)

    mavenCompatibleBaseOpt0 match {
      case None =>

        val repo = IvyRepository.parse(
          patterns.artifactPatterns.head,
          metadataPatternOpt = Some(patterns.ivyPatterns.head),
          changing = None,
          properties = ivyProperties,
          dropInfoAttributes = true
        ) match {
          case Left(err) =>
            sys.error(
              s"Cannot parse Ivy patterns ${patterns.artifactPatterns.head} and ${patterns.ivyPatterns.head}: $err"
            )
          case Right(repo) =>
            repo
        }

        Some(repo)

      case Some(mavenCompatibleBase) =>
        mavenRepositoryOpt(mavenCompatibleBase, log)
    }
  }
}
