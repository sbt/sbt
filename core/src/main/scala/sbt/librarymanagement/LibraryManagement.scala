package sbt.librarymanagement

import java.io.File
import sbt.util.Logger
import sbt.io.Hash
import sbt.librarymanagement.syntax._

/**
 * Helper mixin to provide methods for library management
 */
abstract class LibraryManagement extends LibraryManagementInterface {
  import sbt.internal.librarymanagement.InternalDefaults._
  import sbt.internal.librarymanagement.UpdateClassifiersUtil._

  /**
   * Build a ModuleDescriptor that describes a subproject with dependencies.
   *
   * @param moduleId The root module for which to create a `ModuleDescriptor`.
   * @param directDependencies The direct dependencies of the module.
   * @param scalaModuleInfo The information about the Scala version used, if any.
   * @param configurations The configurations that this module has.
   * @return A `ModuleDescriptor` describing a subproject and its dependencies.
   */
  def moduleDescriptor(moduleId: ModuleID,
                       directDependencies: Vector[ModuleID],
                       scalaModuleInfo: Option[ScalaModuleInfo]): ModuleDescriptor = {
    val moduleSetting = ModuleDescriptorConfiguration(moduleId, ModuleInfo(moduleId.name))
      .withScalaModuleInfo(scalaModuleInfo)
      .withDependencies(directDependencies)
    moduleDescriptor(moduleSetting)
  }

  /**
   * Returns a `ModuleDescriptor` that depends on `dependencyId`.
   *
   * @param dependencyId The module to depend on.
   * @return A `ModuleDescriptor` that depends on `dependencyId`.
   */
  def wrapDependencyInModule(dependencyId: ModuleID): ModuleDescriptor =
    wrapDependencyInModule(dependencyId, None)

  /**
   * Returns a `ModuleDescriptor` that depends on `dependencyId`.
   *
   * @param dependencyId The module to depend on.
   * @param scalaModuleInfo The information about the Scala verson used, if any.
   * @return A `ModuleDescriptor` that depends on `dependencyId`.
   */
  def wrapDependencyInModule(dependencyId: ModuleID,
                             scalaModuleInfo: Option[ScalaModuleInfo]): ModuleDescriptor = {
    val sha1 = Hash.toHex(Hash(dependencyId.name))
    val dummyID = ModuleID(sbtOrgTemp, modulePrefixTemp + sha1, dependencyId.revision)
      .withConfigurations(dependencyId.configurations)
    moduleDescriptor(dummyID, Vector(dependencyId), scalaModuleInfo)
  }

  /**
   * Resolves the given dependency, and retrieves the artifacts to a directory.
   *
   * @param dependencyId The dependency to be resolved.
   * @param scalaModuleInfo The module info about Scala.
   * @param retrieveDirectory The directory to retrieve the files.
   * @param log The logger.
   * @return The result, either an unresolved warning or a sequence of files.
   */
  def retrieve(dependencyId: ModuleID,
               scalaModuleInfo: Option[ScalaModuleInfo],
               retrieveDirectory: File,
               log: Logger): Either[UnresolvedWarning, Vector[File]] =
    retrieve(wrapDependencyInModule(dependencyId, scalaModuleInfo), retrieveDirectory, log)

  /**
   * Resolves the given module's dependencies, and retrieves the artifacts to a directory.
   *
   * @param module The module to be resolved.
   * @param retrieveDirectory The directory to retrieve the files.
   * @param log The logger.
   * @return The result, either an unresolved warning or a sequence of files.
   */
  def retrieve(module: ModuleDescriptor,
               retrieveDirectory: File,
               log: Logger): Either[UnresolvedWarning, Vector[File]] = {
    // Using the default artifact type filter here, so sources and docs are excluded.
    val retrieveConfiguration = RetrieveConfiguration()
      .withRetrieveDirectory(retrieveDirectory)
    val updateConfiguration = UpdateConfiguration()
      .withRetrieveManaged(retrieveConfiguration)
    // .withMissingOk(true)
    log.debug(s"Attempting to fetch ${directDependenciesNames(module)}. This operation may fail.")
    update(
      module,
      updateConfiguration,
      UnresolvedWarningConfiguration(),
      log
    ) match {
      case Left(unresolvedWarning) => Left(unresolvedWarning)
      case Right(updateReport) =>
        val allFiles =
          for {
            conf <- updateReport.configurations
            m <- conf.modules
            (_, f) <- m.artifacts
          } yield f
        log.debug(s"Files retrieved for ${directDependenciesNames(module)}:")
        log.debug(allFiles mkString ", ")
        // allFiles filter predicate match {
        //   case Seq() => None
        //   case files => Some(files)
        // }
        Right(allFiles)
    }
  }

  def transitiveScratch(
      label: String,
      config: GetClassifiersConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {
    import config.{ updateConfiguration => c, module => mod }
    import mod.{ id, dependencies => deps, scalaModuleInfo }
    val base = restrictedCopy(id, true).withName(id.name + "$" + label)
    val module = moduleDescriptor(base, deps, scalaModuleInfo)
    val report = update(module, c, uwconfig, log) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }
    val newConfig = config
      .withModule(mod.withDependencies(report.allModules))
    updateClassifiers(newConfig, uwconfig, Vector(), log)
  }

  /**
   * Creates explicit artifacts for each classifier in `config.module`, and then attempts to resolve them directly. This
   * is for Maven compatibility, where these artifacts are not "published" in the POM, so they don't end up in the Ivy
   * that sbt generates for them either.<br>
   * Artifacts can be obtained from calling toSeq on UpdateReport.<br>
   * In addition, retrieves specific Ivy artifacts if they have one of the requested `config.configuration.types`.
   * @param config important to set `config.configuration.types` to only allow artifact types that can correspond to
   *               "classified" artifacts (sources and javadocs).
   */
  def updateClassifiers(
      config: GetClassifiersConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      artifacts: Vector[(String, ModuleID, Artifact, File)],
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {
    import config.{ updateConfiguration => c, module => mod, _ }
    import mod.{ configurations => confs, _ }
    val artifactFilter = getArtifactTypeFilter(c.artifactFilter)
    assert(classifiers.nonEmpty, "classifiers cannot be empty")
    assert(artifactFilter.types.nonEmpty, "UpdateConfiguration must filter on some types")
    val baseModules = dependencies map { m =>
      restrictedCopy(m, true)
    }
    // Adding list of explicit artifacts here.
    val exls = Map(excludes map { case (k, v) => (k, v.toSet) }: _*)
    val deps = baseModules.distinct flatMap classifiedArtifacts(classifiers, exls, artifacts)
    val base = restrictedCopy(id, true).withName(id.name + classifiers.mkString("$", "_", ""))
    val moduleSetting = ModuleDescriptorConfiguration(base, ModuleInfo(base.name))
      .withScalaModuleInfo(scalaModuleInfo)
      .withDependencies(deps)
      .withConfigurations(confs)
    val module = moduleDescriptor(moduleSetting)

    // c.copy ensures c.types is preserved too
    val upConf = c.withMissingOk(true)
    update(module, upConf, uwconfig, log) match {
      case Right(r) =>
        // The artifacts that came from Ivy don't have their classifier set, let's set it according to
        // FIXME: this is only done because IDE plugins depend on `classifier` to determine type. They
        val typeClassifierMap: Map[String, String] =
          ((sourceArtifactTypes.toIterable map (_ -> Artifact.SourceClassifier))
            :: (docArtifactTypes.toIterable map (_ -> Artifact.DocClassifier)) :: Nil).flatten.toMap
        Right(r.substitute { (conf, mid, artFileSeq) =>
          artFileSeq map {
            case (art, f) =>
              // Deduce the classifier from the type if no classifier is present already
              art.withClassifier(art.classifier orElse typeClassifierMap.get(art.`type`)) -> f
          }
        })
      case Left(w) => Left(w)
    }
  }

  protected def directDependenciesNames(module: ModuleDescriptor): String =
    (module.directDependencies map {
      case mID: ModuleID =>
        import mID._
        s"$organization % $name % $revision"
    }).mkString(", ")
}

/**
 * Helper mixin to provide methods for publisher
 */
abstract class Publisher extends PublisherInterface
