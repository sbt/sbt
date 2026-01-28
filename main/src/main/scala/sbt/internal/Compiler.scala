/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import sbt.internal.inc.{ ScalaInstance, ZincLmUtil }
import sbt.internal.worker.ScalaInstanceConfig
import sbt.librarymanagement.{
  Artifact,
  Configuration,
  Configurations,
  ConfigurationReport,
  ModuleID,
  ScalaArtifacts,
  SemanticSelector,
  VersionNumber
}
import sbt.util.Logger
import xsbti.{ HashedVirtualFileRef, ScalaProvider }

object Compiler:
  def scalaInstanceTask(
      configKey: TaskKey[ScalaInstanceConfig]
  ): Def.Initialize[Task[ScalaInstance]] =
    Def.task {
      val config = configKey.value
      makeScalaInstance(
        config.scalaVersion,
        config.libraryJars.map(File(_)).toArray,
        config.allCompilerJars.map(File(_)),
        config.extraToolJars.map(File(_)),
        Keys.state.value,
        Keys.scalaInstanceTopLoader.value,
      )
    }

  def scalaInstanceConfigTask(
      extraToolConf: Option[Configuration]
  ): Def.Initialize[Task[ScalaInstanceConfig]] =
    Def.taskDyn {
      val sh = Keys.scalaHome.value
      val app = Keys.appConfiguration.value
      val managed = Keys.managedScalaInstance.value
      sh match
        case Some(h) => scalaInstanceConfigFromHome(h)
        case _ =>
          val scalaProvider = app.provider.scalaProvider
          if !managed then emptyScalaInstanceConfig
          else scalaInstanceConfigFromUpdate(extraToolConf)
    }

  /**
   * A dummy ScalaInstance for Java-only projects.
   */
  def emptyScalaInstanceConfig: Def.Initialize[Task[ScalaInstanceConfig]] =
    Def.task {
      ScalaInstanceConfig(
        "0.0.0",
        Vector.empty,
        Vector.empty,
        Vector.empty,
      )
    }

  // Use the same class loader as the Scala classes used by sbt
  // This will fail for "doc" task https://github.com/sbt/sbt/issues/7725
  // because Scala 3 uses ScalaDocTool configuration to manage doc tools.
  def optimizedScalaInstance(
      sv: String,
      scalaProvider: ScalaProvider
  ): Def.Initialize[Task[ScalaInstance]] = Def.task {
    val allJars = scalaProvider.jars
    val libraryJars = allJars.filter { jar =>
      jar.getName == "scala-library.jar" || jar.getName.startsWith("scala3-library_3")
    }
    val compilerJar = allJars.filter { jar =>
      jar.getName == "scala-compiler.jar" || jar.getName.startsWith("scala3-compiler_3")
    }
    compilerJar match
      case Array(compilerJar) if libraryJars.nonEmpty =>
        makeScalaInstance(
          sv,
          libraryJars,
          allJars.toSeq,
          Seq.empty,
          Keys.state.value,
          Keys.scalaInstanceTopLoader.value,
        )
      case _ => ScalaInstance(sv, scalaProvider)
  }

  def scalaInstanceConfigFromHome(dir: File): Def.Initialize[Task[ScalaInstanceConfig]] =
    Def.task {
      val dummy = ScalaInstance(dir)(Keys.state.value.classLoaderCache.apply)
      Seq(dummy.loader, dummy.loaderLibraryOnly).foreach {
        case a: AutoCloseable => a.close()
        case _                =>
      }
      ScalaInstanceConfig(
        dummy.version,
        dummy.libraryJars.toVector.map(_.toPath().toUri()),
        dummy.compilerJars.toVector.map(_.toPath().toUri()),
        dummy.allJars.toVector.map(_.toPath().toUri()),
      )
    }

  def scalaInstanceFromUpdate(
      extraToolConf: Option[Configuration]
  ): Def.Initialize[Task[ScalaInstance]] =
    Def.task {
      val config = scalaInstanceConfigFromUpdate(extraToolConf).value
      makeScalaInstance(
        config.scalaVersion,
        config.libraryJars.map(File(_)).toArray,
        config.allCompilerJars.map(File(_)),
        config.extraToolJars.map(File(_)),
        Keys.state.value,
        Keys.scalaInstanceTopLoader.value,
      )
    }

  def scalaInstanceConfigFromUpdate(
      extraToolConf: Option[Configuration]
  ): Def.Initialize[Task[ScalaInstanceConfig]] = Def.task {
    val sv = Keys.scalaVersion.value
    val fullReport = Keys.update.value
    val s = Keys.streams.value

    // For Scala 3, update scala-library.jar in `scala-tool` and `scala-doc-tool` in case a newer version
    // is present in the `compile` configuration. This is needed once forwards binary compatibility is dropped
    // to avoid NoSuchMethod exceptions when expanding macros.
    def updateLibraryToCompileConfiguration(report: ConfigurationReport) =
      if !ScalaArtifacts.isScala3(sv) then report
      else
        (for {
          compileConf <- fullReport.configuration(Configurations.Compile)
          compileLibMod <- compileConf.modules.find(_.module.name == ScalaArtifacts.LibraryID)
          reportLibMod <- report.modules.find(_.module.name == ScalaArtifacts.LibraryID)
          if VersionNumber(reportLibMod.module.revision)
            .matchesSemVer(SemanticSelector(s"<${compileLibMod.module.revision}"))
        } yield {
          val newMods = report.modules
            .filterNot(_.module.name == ScalaArtifacts.LibraryID) :+ compileLibMod
          report.withModules(newMods)
        }).getOrElse(report)

    val toolReport = updateLibraryToCompileConfiguration(
      fullReport
        .configuration(Configurations.ScalaTool)
        .getOrElse(sys.error(noToolConfiguration(Keys.managedScalaInstance.value)))
    )

    if Classpaths.isScala213(sv) then
      val scalaDeps = for
        compileReport <- fullReport.configuration(Configurations.Compile).iterator
        libName <- ScalaArtifacts.Artifacts.iterator
        lib <- compileReport.modules.find(_.module.name == libName)
      yield lib
      for lib <- scalaDeps.take(1) do
        val libVer = lib.module.revision
        val libName = lib.module.name
        val proj =
          Def.displayBuildRelative(Keys.thisProjectRef.value.build, Keys.thisProjectRef.value)
        if VersionNumber(sv).matchesSemVer(SemanticSelector(s"<$libVer")) then
          val err = !Keys.allowUnsafeScalaLibUpgrade.value
          val fix =
            if err then
              """Upgrade the `scalaVersion` to fix the build. If upgrading the Scala compiler version is
                |not possible (for example due to a regression in the compiler or a missing dependency),
                |this error can be demoted by setting `allowUnsafeScalaLibUpgrade := true`.""".stripMargin
            else s"""Note that the dependency classpath and the runtime classpath of your project
                 |contain the newer $libName $libVer, even if the scalaVersion is $sv.
                 |Compilation (macro expansion) or using the Scala REPL in sbt may fail with a LinkageError.""".stripMargin
          val msg =
            s"""Expected `$proj scalaVersion` to be $libVer or later, but found $sv.
               |To support backwards-only binary compatibility (SIP-51), the Scala 2.13 compiler
               |should not be older than $libName on the dependency classpath.
               |
               |$fix
               |
               |See `$proj evicted` to know why $libName $libVer is getting pulled in.
               |""".stripMargin
          if err then sys.error(msg)
          else s.log.warn(msg)
        else ()
    else ()
    def file(id: String): Option[File] =
      for
        m <- toolReport.modules.find(_.module.name.startsWith(id))
        (_, file) <- m.artifacts.find(_._1.`type` == Artifact.DefaultType)
      yield file

    val allCompilerJars = toolReport.modules.flatMap(_.artifacts.map(_._2))
    val extraToolJars = extraToolConf match
      case Some(extra) =>
        fullReport
          .configuration(extra)
          .map(updateLibraryToCompileConfiguration)
          .toSeq
          .flatMap(_.modules)
          .flatMap(_.artifacts.map(_._2))
      case None => Nil
    val libraryJars = ScalaArtifacts.libraryIds(sv).flatMap(file)
    ScalaInstanceConfig(
      sv,
      libraryJars.toVector.map(_.toPath().toUri()),
      allCompilerJars.map(_.toPath().toUri()),
      extraToolJars.toVector.map(_.toPath().toUri())
    )
  }

  def makeScalaInstance(
      version: String,
      libraryJars: Array[File],
      allCompilerJars: Seq[File],
      extraToolJars: Seq[File],
      state: State,
      topLoader: ClassLoader,
  ): ScalaInstance =
    val classLoaderCache = State.StateOpsImpl(state).extendedClassLoaderCache
    val compilerJars = allCompilerJars.filterNot(libraryJars.contains).distinct.toArray
    val toolJars = extraToolJars
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
      .toArray
    val allJars = libraryJars ++ compilerJars ++ toolJars

    val libraryLoader = classLoaderCache(libraryJars.toList, topLoader)
    val compilerLoader = classLoaderCache(compilerJars.toList, libraryLoader)
    val fullLoader =
      if toolJars.isEmpty then compilerLoader
      else classLoaderCache(toolJars.distinct.toList, compilerLoader)
    new ScalaInstance(
      version = version,
      loader = fullLoader,
      loaderCompilerOnly = compilerLoader,
      loaderLibraryOnly = libraryLoader,
      libraryJars = libraryJars,
      compilerJars = compilerJars,
      allJars = allJars,
      explicitActual = Some(version)
    )

  private def noToolConfiguration(autoInstance: Boolean): String =
    val pre = "Missing Scala tool configuration from the 'update' report.  "
    val post =
      if autoInstance then
        "'scala-tool' is normally added automatically, so this may indicate a bug in sbt or you may be removing it from ivyConfigurations, for example."
      else
        "Explicitly define scalaInstance or scalaHome or include Scala dependencies in the 'scala-tool' configuration."
    pre + post

  def scalaCompilerBridgeJarsTask(
      sourceKey: Def.Initialize[ModuleID],
      log: Logger
  ): Def.Initialize[Task[Seq[HashedVirtualFileRef]]] =
    Def.task {
      val st = Keys.state.value
      val g = BuildPaths.getGlobalBase(st)
      val zincDir = BuildPaths.getZincDirectory(st, g)
      val app = Keys.appConfiguration.value
      val launcher = app.provider.scalaProvider.launcher
      val dr = Keys.scalaCompilerBridgeDependencyResolution.value
      val jars = ZincLmUtil.scalaCompilerBridgeJars(
        scalaInstance = Keys.scalaInstance.value,
        globalLock = launcher.globalLock,
        componentProvider = app.provider.components,
        secondaryCacheDir = Option(zincDir),
        dependencyResolution = dr,
        compilerBridgeSource = Keys.scalaCompilerBridgeSource.value,
        scalaJarsTarget = zincDir,
        log = log
      )
      val conv = Keys.fileConverter.value
      jars.map(jar => (conv.toVirtualFile(jar.toPath()): HashedVirtualFileRef))
    }
end Compiler
