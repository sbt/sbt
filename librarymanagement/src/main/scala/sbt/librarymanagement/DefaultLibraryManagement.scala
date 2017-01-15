package sbt
package librarymanagement

import java.io.File
import scala.util.Try
import sbt.internal.librarymanagement._
import sbt.util.Logger
import sbt.io.Hash

class DefaultLibraryManagement(ivyConfiguration: IvyConfiguration, log: Logger) extends LibraryManagement {
  private[sbt] val ivySbt: IvySbt = new IvySbt(ivyConfiguration, DefaultFileToStore)
  private val sbtOrgTemp = JsonUtil.sbtOrgTemp
  private val modulePrefixTemp = "temp-module-"

  type Module = ivySbt.Module

  /**
   * Returns a dummy module that depends on `moduleID`.
   * Note: Sbt's implementation of Ivy requires us to do this, because only the dependencies
   *       of the specified module will be downloaded.
   */
  def getModule(moduleId: ModuleID): Module = getModule(moduleId, None)

  def getModule(moduleId: ModuleID, ivyScala: Option[IvyScala]): ivySbt.Module = {
    val sha1 = Hash.toHex(Hash(moduleId.name))
    val dummyID = ModuleID(sbtOrgTemp, modulePrefixTemp + sha1, moduleId.revision).withConfigurations(moduleId.configurations)
    getModule(dummyID, Vector(moduleId), UpdateOptions(), ivyScala)
  }

  def getModule(moduleId: ModuleID, deps: Vector[ModuleID],
    uo: UpdateOptions = UpdateOptions(), ivyScala: Option[IvyScala]): ivySbt.Module = {
    val moduleSetting = InlineConfiguration(
      validate = false,
      ivyScala = ivyScala,
      module = moduleId,
      moduleInfo = ModuleInfo(moduleId.name),
      dependencies = deps
    ).withConfigurations(Vector(Configurations.Component))
    new ivySbt.Module(moduleSetting)
  }

  private def dependenciesNames(module: ivySbt.Module): String =
    module.moduleSettings match {
      // `module` is a dummy module, we will only fetch its dependencies.
      case ic: InlineConfiguration =>
        ic.dependencies map {
          case mID: ModuleID =>
            import mID._
            s"$organization % $name % $revision"
        } mkString ", "
      case _ =>
        s"unknown"
    }

  def update(module: ivySbt.Module, retrieveDirectory: File)(predicate: File => Boolean): Option[Seq[File]] = {
    val specialArtifactTypes = Artifact.DefaultSourceTypes union Artifact.DefaultDocTypes
    val artifactFilter = ArtifactTypeFilter.forbid(specialArtifactTypes)
    val retrieveConfiguration = RetrieveConfiguration(retrieveDirectory, Resolver.defaultRetrievePattern).withSync(false)
    val updateConfiguration = UpdateConfiguration(Some(retrieveConfiguration), true, UpdateLogging.DownloadOnly, artifactFilter)

    log.debug(s"Attempting to fetch ${dependenciesNames(module)}. This operation may fail.")
    IvyActions.updateEither(module, updateConfiguration, UnresolvedWarningConfiguration(), LogicalClock.unknown, None, log) match {
      case Left(unresolvedWarning) =>
        log.debug("Couldn't retrieve module ${dependenciesNames(module)}.")
        None

      case Right(updateReport) =>
        val allFiles =
          for {
            conf <- updateReport.configurations
            m <- conf.modules
            (_, f) <- m.artifacts
          } yield f

        log.debug(s"Files retrieved for ${dependenciesNames(module)}:")
        log.debug(allFiles mkString ", ")
        allFiles filter predicate match {
          case Seq() => None
          case files => Some(files)
        }
    }
  }
}
