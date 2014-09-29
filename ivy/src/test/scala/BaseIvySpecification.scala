package sbt

import Path._, Configurations._
import java.io.File
import org.specs2._
import cross.CrossVersionUtil

trait BaseIvySpecification extends Specification {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"
  def currentDependency: File = currentBase / "target" / "dependency"
  def defaultModuleId: ModuleID = ModuleID("com.example", "foo", "0.1.0", Some("compile"))
  lazy val log = ConsoleLogger()
  def module(moduleId: ModuleID, deps: Seq[ModuleID], scalaFullVersion: Option[String],
    uo: UpdateOptions = UpdateOptions()): IvySbt#Module = {
    val ivyScala = scalaFullVersion map { fv =>
      new IvyScala(
        scalaFullVersion = fv,
        scalaBinaryVersion = CrossVersionUtil.binaryScalaVersion(fv),
        configurations = Nil,
        checkExplicit = true,
        filterImplicit = false,
        overrideScalaVersion = false)
    }

    val moduleSetting: ModuleSettings = InlineConfiguration(
      module = moduleId,
      moduleInfo = ModuleInfo("foo"),
      dependencies = deps,
      configurations = Seq(Compile, Test, Runtime),
      ivyScala = ivyScala)
    val ivySbt = new IvySbt(mkIvyConfiguration(uo))
    new ivySbt.Module(moduleSetting)
  }

  def mkIvyConfiguration(uo: UpdateOptions): IvyConfiguration = {
    val paths = new IvyPaths(currentBase, Some(currentTarget))
    val rs = Seq(DefaultMavenRepository)
    val other = Nil
    val moduleConfs = Seq(ModuleConfiguration("*", DefaultMavenRepository))
    val off = false
    val check = Nil
    val resCacheDir = currentTarget / "resolution-cache"
    new InlineIvyConfiguration(paths, rs, other, moduleConfs, off, None, check, Some(resCacheDir), uo, log)
  }

  def ivyUpdateEither(module: IvySbt#Module): Either[UnresolvedWarning, UpdateReport] = {
    // IO.delete(currentTarget)
    val retrieveConfig = new RetrieveConfiguration(currentManaged, Resolver.defaultRetrievePattern)
    val config = new UpdateConfiguration(Some(retrieveConfig), false, UpdateLogging.Full)
    IvyActions.updateEither(module, config, UnresolvedWarningConfiguration(), LogicalClock.unknown, Some(currentDependency), log)
  }

  def ivyUpdate(module: IvySbt#Module) =
    ivyUpdateEither(module) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }
}
