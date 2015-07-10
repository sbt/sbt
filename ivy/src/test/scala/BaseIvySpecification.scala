package sbt

import Path._, Configurations._
import java.io.File
import org.specs2._
import cross.CrossVersionUtil
import sbt.PublishConfiguration
import sbt.ivyint.SbtChainResolver

trait BaseIvySpecification extends Specification {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"
  def currentDependency: File = currentBase / "target" / "dependency"
  def defaultModuleId: ModuleID = ModuleID("com.example", "foo", "0.1.0", Some("compile"))
  lazy val log = ConsoleLogger()

  def configurations = Seq(Compile, Test, Runtime)
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
      configurations = configurations,
      ivyScala = ivyScala)
    val ivySbt = new IvySbt(mkIvyConfiguration(uo))
    new ivySbt.Module(moduleSetting)
  }

  def resolvers: Seq[Resolver] = Seq(DefaultMavenRepository)

  def chainResolver = ChainedResolver("sbt-chain", resolvers)

  def mkIvyConfiguration(uo: UpdateOptions): IvyConfiguration = {
    val paths = new IvyPaths(currentBase, Some(currentTarget))
    val other = Nil
    val moduleConfs = Seq(ModuleConfiguration("*", chainResolver))
    val off = false
    val check = Nil
    val resCacheDir = currentTarget / "resolution-cache"
    new InlineIvyConfiguration(paths, resolvers, other, moduleConfs, off, None, check, Some(resCacheDir), uo, log)
  }

  def ivyUpdateEither(module: IvySbt#Module): Either[UnresolvedWarning, UpdateReport] = {
    // IO.delete(currentTarget)
    val retrieveConfig = new RetrieveConfiguration(currentManaged, Resolver.defaultRetrievePattern, false)
    val config = new UpdateConfiguration(Some(retrieveConfig), false, UpdateLogging.Full)
    IvyActions.updateEither(module, config, UnresolvedWarningConfiguration(), LogicalClock.unknown, Some(currentDependency), log)
  }

  def cleanCachedResolutionCache(module: IvySbt#Module): Unit =
    {
      IvyActions.cleanCachedResolutionCache(module, log)
    }

  def ivyUpdate(module: IvySbt#Module) =
    ivyUpdateEither(module) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }

  def mkPublishConfiguration(resolver: Resolver, artifacts: Map[Artifact, File]): PublishConfiguration = {
    new PublishConfiguration(
      ivyFile = None,
      resolverName = resolver.name,
      artifacts = artifacts,
      checksums = Seq(),
      logging = UpdateLogging.Full,
      overwrite = true)
  }

  def ivyPublish(module: IvySbt#Module, config: PublishConfiguration) = {
    IvyActions.publish(module, config, log)
  }
}
