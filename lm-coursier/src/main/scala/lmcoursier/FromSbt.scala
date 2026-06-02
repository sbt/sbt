package lmcoursier

import coursier.ivy.IvyXml.mappings as ivyXmlMappings
import lmcoursier.definitions.{
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Info,
  Module,
  ModuleName,
  Organization,
  Project,
  Publication,
  Type
}
import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.librarymanagement.{ Configuration as _, * }

object FromSbt {

  private def sbtCrossName(
      name: String,
      crossVersion: CrossVersion,
      platformOpt: Option[String],
      scalaVersion: => String,
      scalaBinaryVersion: => String,
      optionalCrossVer: Boolean = false,
      projectPlatform: Option[String],
  ): String = {
    val name0 = name
    val name1 =
      crossVersion match
        case _: Disabled => name0
        case _           => addPlatformSuffix(name0, platformOpt, projectPlatform)
    val updatedName = CrossVersion(crossVersion, scalaVersion, scalaBinaryVersion)
      .fold(name1)(_(name1))
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

  // Duplicate of sbt.librarymanagement.CrossVersion.addPlatformSuffix. Keep the two in sync
  // until lm-coursier moves under sbt
  private def addPlatformSuffix(
      name: String,
      platformOpt: Option[String],
      projectPlatform: Option[String]
  ): String = {
    def addSuffix(platformName: String): String =
      platformName match {
        case "" | "jvm" => name
        case _          => s"${name}_$platformName"
      }
    (platformOpt, projectPlatform) match {
      case (Some(p), _) =>
        addSuffix(p) // Use explicit platform if set (don't override with project platform)
      case (None, Some(p)) =>
        addSuffix(p) // Only use project platform if dependency has no explicit platform
      case _ => name
    }
  }

  private def attributes(attr: Map[String, String]): Map[String, String] =
    attr
      .map { (k, v) =>
        k.stripPrefix("e:") -> v
      }
      .filter { case (k, _) =>
        !k.startsWith(SbtPomExtraProperties.POM_INFO_KEY_PREFIX)
      }

  def moduleVersion(
      module: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String,
      optionalCrossVer: Boolean,
      projectPlatform: Option[String],
  ): (Module, String) = {

    val fullName =
      sbtCrossName(
        module.name,
        module.crossVersion,
        module.platformOpt,
        scalaVersion,
        scalaBinaryVersion,
        optionalCrossVer,
        projectPlatform
      )

    val module0 = Module(
      Organization(module.organization),
      ModuleName(fullName),
      attributes(module.extraDependencyAttributes)
    )
    val version = module.revision

    (module0, version)
  }

  def moduleVersion(
      module: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String
  ): (Module, String) =
    moduleVersion(
      module,
      scalaVersion,
      scalaBinaryVersion,
      optionalCrossVer = false,
      projectPlatform = None
    )

  def dependencies(
      module: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String,
      optionalCrossVer: Boolean = false,
      projectPlatform: Option[String] = None,
  ): Seq[(Configuration, Dependency)] = {

    // TODO Warn about unsupported properties in `module`

    val (module0, version) =
      moduleVersion(module, scalaVersion, scalaBinaryVersion, optionalCrossVer, projectPlatform)

    val dep = Dependency(
      module0,
      version,
      Configuration(""),
      exclusions = module.exclusions.map { rule =>
        // FIXME Other `rule` fields are ignored here
        val ruleFullName = sbtCrossName(
          rule.name,
          rule.crossVersion,
          platformOpt = None,
          scalaVersion,
          scalaBinaryVersion,
          optionalCrossVer,
          projectPlatform
        )
        (Organization(rule.organization), ModuleName(ruleFullName))
      }.toSet,
      Publication("", Type(""), Extension(""), Classifier("")),
      optional = false,
      transitive = module.isTransitive
    )

    val mapping = module.configurations.getOrElse("compile")
    val allMappings = ivyXmlMappings(mapping).map { (from, to) =>
      (Configuration(from.value), Configuration(to.value))
    }

    val publications =
      if (module.explicitArtifacts.isEmpty)
        Seq(Publication("", Type(""), Extension(""), Classifier("")))
      else
        module.explicitArtifacts
          .map { a =>
            Publication(
              name = a.name,
              `type` = Type(a.`type`),
              ext = Extension(a.extension),
              classifier = a.classifier.fold(Classifier(""))(Classifier(_))
            )
          }

    for {
      (from, to) <- allMappings.distinct
      pub <- publications.distinct
    } yield {
      val dep0 = dep
        .withConfiguration(to)
        .withPublication(pub)
      from -> dep0
    }
  }

  def fallbackDependencies(
      allDependencies: Seq[ModuleID],
      scalaVersion: String,
      scalaBinaryVersion: String
  ): Seq[FallbackDependency] =
    for {
      module <- allDependencies
      artifact <- module.explicitArtifacts
      uri <- artifact.url.toSeq
    } yield {
      val (module0, version) = moduleVersion(module, scalaVersion, scalaBinaryVersion)
      FallbackDependency(module0, version, uri, module.isChanging)
    }

  def project(
      projectID: ModuleID,
      allDependencies: Seq[ModuleID],
      ivyConfigurations: Map[Configuration, Seq[Configuration]],
      scalaVersion: String,
      scalaBinaryVersion: String,
      projectPlatform: Option[String],
  ): Project = {

    val deps = allDependencies.flatMap(
      dependencies(_, scalaVersion, scalaBinaryVersion, projectPlatform = projectPlatform)
    )

    val prefix = "e:" + SbtPomExtraProperties.POM_INFO_KEY_PREFIX
    val properties = projectID.extraAttributes.view
      .filterKeys(_.startsWith(prefix))
      .toSeq
      .map { (k, v) => (k.stripPrefix("e:"), v) }
      .sortBy(_._1)

    Project(
      Module(
        Organization(projectID.organization),
        ModuleName(
          sbtCrossName(
            projectID.name,
            projectID.crossVersion,
            projectID.platformOpt,
            scalaVersion,
            scalaBinaryVersion,
            projectPlatform = projectPlatform
          )
        ),
        attributes(projectID.extraDependencyAttributes)
      ),
      projectID.revision,
      deps,
      ivyConfigurations,
      properties,
      None,
      Nil,
      Info("", "", Nil, Nil, None)
    )
  }
}
