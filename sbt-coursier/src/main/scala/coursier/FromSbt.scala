package coursier

import coursier.ivy.IvyRepository
import coursier.ivy.IvyXml.{mappings => ivyXmlMappings}
import java.net.{MalformedURLException, URL}

import coursier.core.Authentication
import sbt.{CrossVersion, ModuleID, Resolver}

import SbtCompatibility._

object FromSbt {

  def sbtModuleIdName(
    moduleId: ModuleID,
    scalaVersion: => String,
    scalaBinaryVersion: => String
  ): String =
    sbtCrossVersionName(moduleId.name, moduleId.crossVersion, scalaVersion, scalaBinaryVersion)

  def sbtCrossVersionName(
    name: String,
    crossVersion: CrossVersion,
    scalaVersion: => String,
    scalaBinaryVersion: => String
  ): String = crossVersion match {
    case _: Disabled => name
    case f: Full => name + "_" + f.remapVersion(scalaVersion)
    case b: Binary => name + "_" + b.remapVersion(scalaBinaryVersion)
  }

  def attributes(attr: Map[String, String]): Map[String, String] =
    attr.map { case (k, v) =>
      k.stripPrefix("e:") -> v
    }.filter { case (k, _) =>
      !k.startsWith(SbtPomExtraProperties.POM_INFO_KEY_PREFIX)
    }

  def moduleVersion(
    module: ModuleID,
    scalaVersion: String,
    scalaBinaryVersion: String
  ): (Module, String) = {

    val fullName = sbtModuleIdName(module, scalaVersion, scalaBinaryVersion)

    val module0 = Module(module.organization, fullName, FromSbt.attributes(module.extraDependencyAttributes))
    val version = module.revision

    (module0, version)
  }

  def dependencies(
    module: ModuleID,
    scalaVersion: String,
    scalaBinaryVersion: String
  ): Seq[(String, Dependency)] = {

    // TODO Warn about unsupported properties in `module`

    val (module0, version) = moduleVersion(module, scalaVersion, scalaBinaryVersion)

    val dep = Dependency(
      module0,
      version,
      exclusions = module.exclusions.map { rule =>
        // FIXME Other `rule` fields are ignored here
        (rule.organization, rule.name)
      }.toSet,
      transitive = module.isTransitive
    )

    val mapping = module.configurations.getOrElse("compile")
    val allMappings = ivyXmlMappings(mapping)

    val attributes =
      if (module.explicitArtifacts.isEmpty)
        Seq(Attributes("", ""))
      else
        module.explicitArtifacts.map { a =>
          Attributes(`type` = a.`type`, classifier = a.classifier.getOrElse(""))
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
  ): Seq[(Module, String, URL, Boolean)] =
    for {
      module <- allDependencies
      artifact <- module.explicitArtifacts
      url <- artifact.url.toSeq
    } yield {
      val (module0, version) = moduleVersion(module, scalaVersion, scalaBinaryVersion)
      (module0, version, url, module.isChanging)
    }

  def project(
    projectID: ModuleID,
    allDependencies: Seq[ModuleID],
    ivyConfigurations: Map[String, Seq[String]],
    scalaVersion: String,
    scalaBinaryVersion: String
  ): Project = {

    val deps = allDependencies.flatMap(dependencies(_, scalaVersion, scalaBinaryVersion))

    Project(
      Module(
        projectID.organization,
        sbtModuleIdName(projectID, scalaVersion, scalaBinaryVersion),
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
      None,
      Nil,
      Info.empty
    )
  }

  private def mavenCompatibleBaseOpt(patterns: sbt.Patterns): Option[String] =
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
    log: sbt.Logger,
    authentication: Option[Authentication]
  ): Option[MavenRepository] =
    try {
      Cache.url(root) // ensure root is a URL whose protocol can be handled here
      val root0 = if (root.endsWith("/")) root else root + "/"
      Some(
        MavenRepository(
          root0,
          authentication = authentication
        )
      )
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
    log: sbt.Logger,
    authentication: Option[Authentication]
  ): Option[Repository] =
    resolver match {
      case r: SbtCompatibility.MavenRepository =>
        mavenRepositoryOpt(r.root, log, authentication)

      case r: sbt.FileRepository
        if r.patterns.ivyPatterns.lengthCompare(1) == 0 &&
          r.patterns.artifactPatterns.lengthCompare(1) == 0 =>

        val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(r.patterns)

        mavenCompatibleBaseOpt0 match {
          case None =>
            Some(IvyRepository(
              "file://" + r.patterns.artifactPatterns.head,
              metadataPatternOpt = Some("file://" + r.patterns.ivyPatterns.head),
              changing = Some(true),
              properties = ivyProperties,
              dropInfoAttributes = true,
              authentication = authentication
            ))
          case Some(mavenCompatibleBase) =>
            mavenRepositoryOpt("file://" + mavenCompatibleBase, log, authentication)
        }

      case r: sbt.URLRepository
        if r.patterns.ivyPatterns.lengthCompare(1) == 0 &&
          r.patterns.artifactPatterns.lengthCompare(1) == 0 =>

        val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(r.patterns)

        mavenCompatibleBaseOpt0 match {
          case None =>
            Some(IvyRepository(
              r.patterns.artifactPatterns.head,
              metadataPatternOpt = Some(r.patterns.ivyPatterns.head),
              changing = None,
              properties = ivyProperties,
              dropInfoAttributes = true,
              authentication = authentication
            ))
          case Some(mavenCompatibleBase) =>
            mavenRepositoryOpt(mavenCompatibleBase, log, authentication)
        }

      case raw: sbt.RawRepository if raw.name == "inter-project" => // sbt.RawRepository.equals just compares names anyway
        None

      case other =>
        log.warn(s"Unrecognized repository ${other.name}, ignoring it")
        None
    }

}
