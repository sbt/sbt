package sbt

import Path._, Configurations._
import java.io.File
import org.specs2._
import cross.CrossVersionUtil

trait BaseIvySpecification extends Specification {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def defaultModuleId: ModuleID = ModuleID("com.example", "foo", "0.1.0", Some("compile"))
  lazy val ivySbt = new IvySbt(mkIvyConfiguration)
  lazy val log = Logger.Null
  def module(moduleId: ModuleID, deps: Seq[ModuleID], scalaFullVersion: Option[String]): IvySbt#Module = {
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
    new ivySbt.Module(moduleSetting)
  }

  def mkIvyConfiguration: IvyConfiguration = {
    val paths = new IvyPaths(currentBase, Some(currentTarget))
    val rs = Seq(DefaultMavenRepository)
    val other = Nil
    val moduleConfs = Seq(ModuleConfiguration("*", DefaultMavenRepository))
    val off = false
    val check = Nil
    val resCacheDir = currentTarget / "resolution-cache"
    val uo = UpdateOptions()
    new InlineIvyConfiguration(paths, rs, other, moduleConfs, off, None, check, Some(resCacheDir), uo, log)
  }

  def ivyUpdate(module: IvySbt#Module) = {
    // IO.delete(currentTarget)
    val config = new UpdateConfiguration(None, false, UpdateLogging.Full)
    IvyActions.update(module, config, log)
  }
}
