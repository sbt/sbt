package lmcoursier

import coursier.ivy.IvyXml.{mappings => ivyXmlMappings}
import lmcoursier.definitions.{Classifier, Configuration, Dependency, Extension, Info, Module, ModuleName, Organization, Project, Publication, Type}
import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.librarymanagement.{Configuration => _, MavenRepository => _, _}

object FromSbt {

  private def sbtModuleIdName(
    moduleId: ModuleID,
    scalaVersion: => String,
    scalaBinaryVersion: => String,
    optionalCrossVer: Boolean = false
  ): String = {
    val name0 = moduleId.name
    val updatedName = CrossVersion(moduleId.crossVersion, scalaVersion, scalaBinaryVersion)
      .fold(name0)(_(name0))
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

  private def attributes(attr: Map[String, String]): Map[String, String] =
    attr.map { case (k, v) =>
      k.stripPrefix("e:") -> v
    }.filter { case (k, _) =>
      !k.startsWith(SbtPomExtraProperties.POM_INFO_KEY_PREFIX)
    }

  def moduleVersion(
    module: ModuleID,
    scalaVersion: String,
    scalaBinaryVersion: String,
    optionalCrossVer: Boolean
  ): (Module, String) = {

    val fullName = sbtModuleIdName(module, scalaVersion, scalaBinaryVersion, optionalCrossVer)

    val module0 = Module(Organization(module.organization), ModuleName(fullName), attributes(module.extraDependencyAttributes))
    val version = module.revision

    (module0, version)
  }

  def moduleVersion(
    module: ModuleID,
    scalaVersion: String,
    scalaBinaryVersion: String
  ): (Module, String) =
    moduleVersion(module, scalaVersion, scalaBinaryVersion, optionalCrossVer = false)

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
      Configuration(""),
      exclusions = module.exclusions.map { rule =>
        // FIXME Other `rule` fields are ignored here
        (Organization(rule.organization), ModuleName(rule.name))
      }.toSet,
      Publication("", Type(""), Extension(""), Classifier("")),
      optional = false,
      transitive = module.isTransitive
    )

    val mapping = module.configurations.getOrElse("compile")
    val allMappings = ivyXmlMappings(mapping).map {
      case (from, to) =>
        (Configuration(from.value), Configuration(to.value))
    }

    val publications =
      if (module.explicitArtifacts.isEmpty)
        Seq(Publication("", Type(""), Extension(""), Classifier("")))
      else
        module
          .explicitArtifacts
          .map { a =>
            Publication(
              name = a.name,
              `type` = Type(a.`type`),
              ext = Extension(a.extension),
              classifier = a.classifier.fold(Classifier(""))(Classifier(_))
            )
          }

    for {
      (from, to) <- allMappings
      pub <- publications
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
      url <- artifact.url.toSeq
    } yield {
      val (module0, version) = moduleVersion(module, scalaVersion, scalaBinaryVersion)
      FallbackDependency(module0, version, url, module.isChanging)
    }

  def project(
    projectID: ModuleID,
    allDependencies: Seq[ModuleID],
    ivyConfigurations: Map[Configuration, Seq[Configuration]],
    scalaVersion: String,
    scalaBinaryVersion: String
  ): Project = {

    val deps = allDependencies.flatMap(dependencies(_, scalaVersion, scalaBinaryVersion))

    val prefix = "e:" + SbtPomExtraProperties.POM_INFO_KEY_PREFIX
    val properties = projectID
      .extraAttributes
      .filterKeys(_.startsWith(prefix))
      .toSeq
      .map { case (k, v) => (k.stripPrefix("e:"), v) }
      .sortBy(_._1)

    Project(
      Module(
        Organization(projectID.organization),
        ModuleName(sbtModuleIdName(projectID, scalaVersion, scalaBinaryVersion)),
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
