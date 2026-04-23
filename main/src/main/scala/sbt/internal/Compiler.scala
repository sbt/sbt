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
import sbt.internal.inc.ScalaInstance
import sbt.librarymanagement.{
  Artifact,
  Configuration,
  Configurations,
  ConfigurationReport,
  ScalaArtifacts,
  SemanticSelector,
  UpdateReport,
  VersionNumber
}
import xsbti.ScalaProvider

private[sbt] object Compiler {

  /**
   * Returns a ScalaInstance.
   * extraToolConf is used for Scala 3 since it started splitting up scaladoc and repl.
   */
  def scalaInstanceTask(extraToolConf: Option[Configuration]): Def.Initialize[Task[ScalaInstance]] =
    Def.taskDyn {
      val sh = Keys.scalaHome.value
      val app = Keys.appConfiguration.value
      val managed = Keys.managedScalaInstance.value
      val sv = Keys.scalaVersion.value
      val configs = Keys.ivyConfigurations.value
      // if this logic changes, ensure that `unmanagedScalaInstanceOnly` and `update` are changed
      //  appropriately to avoid cycles
      (sh, extraToolConf) match {
        case (Some(h), _) => scalaInstanceFromHome(h)
        case _ if !managed =>
          val extra = extraToolConf.getOrElse(Configurations.ScalaTool)
          if (configs.contains(extra)) scalaInstanceFromUpdate(extraToolConf)
          else emptyScalaInstance
        case _ =>
          val scalaProvider = app.provider.scalaProvider
          if (sv == scalaProvider.version) optimizedScalaInstance(sv, scalaProvider)
          else scalaInstanceFromUpdate(extraToolConf)
      }
    }

  /**
   * A dummy ScalaInstance for Java-only projects.
   */
  def emptyScalaInstance: Def.Initialize[Task[ScalaInstance]] = Def.task {
    makeScalaInstance(
      "0.0.0",
      Array.empty,
      Seq.empty,
      Seq.empty,
      Keys.state.value,
      Keys.scalaInstanceTopLoader.value,
    )
  }

  // Use the same class loader as the Scala classes used by sbt
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
    compilerJar match {
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
  }

  def scalaInstanceFromHome(dir: File): Def.Initialize[Task[ScalaInstance]] = Def.task {
    val dummy = ScalaInstance(dir)(Keys.state.value.classLoaderCache.apply)
    Seq(dummy.loader, dummy.loaderLibraryOnly).foreach {
      case a: AutoCloseable => a.close()
      case _                =>
    }
    makeScalaInstance(
      dummy.version,
      dummy.libraryJars,
      dummy.compilerJars.toSeq,
      dummy.allJars.toSeq,
      Keys.state.value,
      Keys.scalaInstanceTopLoader.value,
    )
  }

  /**
   * Returns a ScalaInstance.
   * extraToolConf is used for Scala 3 since it started splitting up scaladoc and repl.
   */
  def scalaInstanceFromUpdate(
      extraToolConf: Option[Configuration]
  ): Def.Initialize[Task[ScalaInstance]] =
    Def.task {
      val sv = Keys.scalaVersion.value
      val fullReport = Keys.update.value
      val s = Keys.streams.value

      val toolReport = updateLibraryToCompileConfiguration(sv, fullReport)(
        fullReport
          .configuration(Configurations.ScalaTool)
          .getOrElse(sys.error(noToolConfiguration(Keys.managedScalaInstance.value)))
      )

      if (Classpaths.isScala213(sv)) {
        val scalaDeps = for {
          compileReport <- fullReport.configuration(Configurations.Compile).iterator
          libName <- ScalaArtifacts.Artifacts.iterator
          lib <- compileReport.modules.find(_.module.name == libName)
        } yield lib
        for (lib <- scalaDeps.take(1)) {
          val libVer = lib.module.revision
          val libName = lib.module.name
          val proj =
            Def.displayBuildRelative(Keys.thisProjectRef.value.build, Keys.thisProjectRef.value)
          if (VersionNumber(sv).matchesSemVer(SemanticSelector(s"<$libVer"))) {
            val err = !Keys.allowUnsafeScalaLibUpgrade.value
            val fix =
              if (err)
                """Upgrade the `scalaVersion` to fix the build. If upgrading the Scala compiler version is
                |not possible (for example due to a regression in the compiler or a missing dependency),
                |this error can be demoted by setting `allowUnsafeScalaLibUpgrade := true`.""".stripMargin
              else
                s"""Note that the dependency classpath and the runtime classpath of your project
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
            if (err) sys.error(msg)
            else s.log.warn(msg)
          }
        }
      }
      def file(id: String): Option[File] = {
        for {
          m <- toolReport.modules.find(_.module.name.startsWith(id))
          (_, file) <- m.artifacts.find(_._1.`type` == Artifact.DefaultType)
        } yield file
      }

      val allCompilerJars = toolReport.modules.flatMap(_.artifacts.map(_._2))
      val extraToolJars =
        extraToolConf match {
          case Some(extra) =>
            fullReport
              .configuration(extra)
              .map(updateLibraryToCompileConfiguration(sv, fullReport))
              .toSeq
              .flatMap(_.modules)
              .flatMap(_.artifacts.map(_._2))
          case None => Nil
        }
      val libraryJars = ScalaArtifacts.libraryIds(sv).flatMap(file)

      makeScalaInstance(
        sv,
        libraryJars,
        allCompilerJars,
        extraToolJars,
        Keys.state.value,
        Keys.scalaInstanceTopLoader.value,
      )
    }

  // For Scala 3, update scala-library.jar in `scala-tool` and `scala-doc-tool` in case a newer version
  // is present in the `compile` configuration. This is needed once forwards binary compatibility is dropped
  // to avoid NoSuchMethod exceptions when expanding macros.
  private def updateLibraryToCompileConfiguration(sv: String, fullReport: UpdateReport)(
      report: ConfigurationReport
  ) =
    if (!ScalaArtifacts.isScala3(sv)) report
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

  def makeScalaInstance(
      version: String,
      libraryJars: Array[File],
      allCompilerJars: Seq[File],
      extraToolJars: Seq[File],
      state: State,
      topLoader: ClassLoader,
  ): ScalaInstance = {
    val classLoaderCache = state.extendedClassLoaderCache
    val compilerJars = allCompilerJars.filterNot(libraryJars.contains).distinct.toArray
    val toolJars = extraToolJars
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
      .toArray
    val allJars = libraryJars ++ compilerJars ++ toolJars

    val libraryLoader = classLoaderCache(libraryJars.toList, topLoader)
    val compilerLoader = classLoaderCache(compilerJars.toList, libraryLoader)
    val fullLoader =
      if (toolJars.isEmpty) compilerLoader
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
  }

  private def noToolConfiguration(autoInstance: Boolean): String = {
    val pre = "Missing Scala tool configuration from the 'update' report.  "
    val post =
      if (autoInstance)
        "'scala-tool' is normally added automatically, so this may indicate a bug in sbt or you may be removing it from ivyConfigurations, for example."
      else
        "Explicitly define scalaInstance or scalaHome or include Scala dependencies in the 'scala-tool' configuration."
    pre + post
  }
}
