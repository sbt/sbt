package sbt

import java.io.File
import scala.util.Try

private[sbt] class UpdateUtil(ivyConfiguration: IvyConfiguration, log: Logger) {
  private[sbt] val ivySbt: IvySbt = new IvySbt(ivyConfiguration)
  private val sbtOrgTemp = JsonUtil.sbtOrgTemp
  private val modulePrefixTemp = "temp-module-"
  // private val buffered = new BufferedLogger(FullLogger(log))

  /**
   * Returns a dummy module that depends on `moduleID`.
   * Note: Sbt's implementation of Ivy requires us to do this, because only the dependencies
   *       of the specified module will be downloaded.
   */
  def getModule(moduleId: ModuleID): ivySbt.Module = getModule(moduleId, None)

  def getModule(moduleId: ModuleID, ivyScala: Option[IvyScala]): ivySbt.Module = {
    val sha1 = Hash.toHex(Hash(moduleId.name))
    val dummyID = ModuleID(sbtOrgTemp, modulePrefixTemp + sha1, moduleId.revision, moduleId.configurations)
    getModule(dummyID, Seq(moduleId), UpdateOptions(), ivyScala)
  }

  def getModule(moduleId: ModuleID, deps: Seq[ModuleID],
    uo: UpdateOptions = UpdateOptions(), ivyScala: Option[IvyScala]): ivySbt.Module = {
    val moduleSetting = InlineConfiguration(
      module = moduleId,
      moduleInfo = ModuleInfo(moduleId.name),
      dependencies = deps,
      configurations = Seq(Configurations.Component),
      ivyScala = ivyScala)
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

    val retrieveConfiguration = new RetrieveConfiguration(retrieveDirectory, Resolver.defaultRetrievePattern, false)
    val updateConfiguration = new UpdateConfiguration(Some(retrieveConfiguration), true, UpdateLogging.DownloadOnly)

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
