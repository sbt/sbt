package coursier

import coursier.ivy.{ IvyXml, IvyRepository }

import java.net.{ MalformedURLException, URL }

import sbt.{ Resolver, CrossVersion, ModuleID }
import sbt.mavenint.SbtPomExtraProperties

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
    case CrossVersion.Disabled => name
    case f: CrossVersion.Full => name + "_" + f.remapVersion(scalaVersion)
    case f: CrossVersion.Binary => name + "_" + f.remapVersion(scalaBinaryVersion)
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
    val allMappings = IvyXml.mappings(mapping)

    val attributes =
      if (module.explicitArtifacts.isEmpty)
        Seq(Attributes())
      else
        module.explicitArtifacts.map { a =>
          Attributes(`type` = a.extension, classifier = a.classifier.getOrElse(""))
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

    // FIXME Ignored for now - easy to support though
    // val sbtDepOverrides = dependencyOverrides.value
    // val sbtExclusions = excludeDependencies.value

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
      Nil,
      Info.empty
    )
  }

  def repository(
    resolver: Resolver,
    ivyProperties: Map[String, String],
    log: sbt.Logger
  ): Option[Repository] =
    resolver match {
      case sbt.MavenRepository(_, root) =>
        try {
          Cache.url(root) // ensure root is a URL whose protocol can be handled here
          val root0 = if (root.endsWith("/")) root else root + "/"
          Some(MavenRepository(root0, sbtAttrStub = true))
        } catch {
          case e: MalformedURLException =>
            log.warn(
              "Error parsing Maven repository base " +
              root +
              Option(e.getMessage).map(" (" + _ + ")").mkString +
              ", ignoring it"
            )

            None
        }

      case sbt.FileRepository(_, _, patterns)
        if patterns.ivyPatterns.lengthCompare(1) == 0 &&
          patterns.artifactPatterns.lengthCompare(1) == 0 =>

        Some(IvyRepository(
          "file://" + patterns.artifactPatterns.head,
          metadataPatternOpt = Some("file://" + patterns.ivyPatterns.head),
          changing = Some(true),
          properties = ivyProperties,
          dropInfoAttributes = true
        ))

      case sbt.URLRepository(_, patterns)
        if patterns.ivyPatterns.lengthCompare(1) == 0 &&
          patterns.artifactPatterns.lengthCompare(1) == 0 =>

        Some(IvyRepository(
          patterns.artifactPatterns.head,
          metadataPatternOpt = Some(patterns.ivyPatterns.head),
          changing = None,
          properties = ivyProperties,
          dropInfoAttributes = true
        ))

      case other =>
        log.warn(s"Unrecognized repository ${other.name}, ignoring it")
        None
    }

}
