package sbt
package internal

import java.io.File
import sbt.internal.inc.ScalaInstance
import sbt.internal.worker.ScalaInstanceConfig
import sbt.librarymanagement.{
  Artifact,
  Configurations,
  ConfigurationReport,
  ScalaArtifacts,
  SemanticSelector,
  VersionNumber
}
import xsbti.ScalaProvider

object Compiler:
  def scalaInstanceTask: Def.Initialize[Task[ScalaInstance]] =
    Def.task {
      val config = scalaInstanceConfigTask.value
      makeScalaInstance(
        config.scalaVersion,
        config.libraryJars.map(File(_)).toArray,
        config.allCompilerJars.map(File(_)),
        config.allDocJars.map(File(_)),
        Keys.state.value,
        Keys.scalaInstanceTopLoader.value,
      )
    }

  def scalaInstanceConfigTask: Def.Initialize[Task[ScalaInstanceConfig]] =
    Def.taskDyn {
      val sh = Keys.scalaHome.value
      val app = Keys.appConfiguration.value
      val sv = Keys.scalaVersion.value
      sh match
        case Some(h) => scalaInstanceConfigFromHome(h)
        case _ =>
          val scalaProvider = app.provider.scalaProvider
          scalaInstanceConfigFromUpdate
    }

  // use the same class loader as the Scala classes used by sbt
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

  def scalaInstanceConfigFromHome(dir: File): Def.Initialize[Task[ScalaInstanceConfig]] = Def.task {
    val dummy = ScalaInstance(dir)(Keys.state.value.classLoaderCache.apply)
    Seq(dummy.loader, dummy.loaderLibraryOnly).foreach {
      case a: AutoCloseable => a.close()
      case _                =>
    }
    ScalaInstanceConfig(
      dummy.version,
      dummy.libraryJars.toVector.map(_.toString()),
      dummy.compilerJars.toVector.map(_.toString()),
      dummy.allJars.toVector.map(_.toString()),
    )
  }

  def scalaInstanceConfigFromUpdate: Def.Initialize[Task[ScalaInstanceConfig]] = Def.task {
    val sv = Keys.scalaVersion.value
    val fullReport = Keys.update.value

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

    if (Classpaths.isScala213(sv)) {
      for {
        compileReport <- fullReport.configuration(Configurations.Compile)
        libName <- ScalaArtifacts.Artifacts
      } {
        for (lib <- compileReport.modules.find(_.module.name == libName)) {
          val libVer = lib.module.revision
          val n = Keys.name.value
          if (VersionNumber(sv).matchesSemVer(SemanticSelector(s"<$libVer")))
            sys.error(
              s"""expected `$n/scalaVersion` to be "$libVer" or later,
                 |but found "$sv"; upgrade scalaVersion to fix the build.
                 |
                 |to support backwards-only binary compatibility (SIP-51),
                 |the Scala 2.13 compiler cannot be older than $libName on the
                 |dependency classpath.
                 |see `$n/evicted` to know why $libName $libVer is getting pulled in.
                 |""".stripMargin
            )
        }
      }
    }
    def file(id: String): File = {
      val files = for {
        m <- toolReport.modules if m.module.name.startsWith(id)
        (art, file) <- m.artifacts if art.`type` == Artifact.DefaultType
      } yield file
      files.headOption getOrElse sys.error(s"Missing $id jar file")
    }

    val allCompilerJars = toolReport.modules.flatMap(_.artifacts.map(_._2))
    val allDocJars =
      fullReport
        .configuration(Configurations.ScalaDocTool)
        .map(updateLibraryToCompileConfiguration)
        .toSeq
        .flatMap(_.modules)
        .flatMap(_.artifacts.map(_._2))
    val libraryJars = ScalaArtifacts.libraryIds(sv).map(file)

    ScalaInstanceConfig(
      sv,
      libraryJars.toVector.map(_.toString),
      allCompilerJars.toVector.map(_.toString),
      allDocJars.toVector.map(_.toString),
      // Keys.state.value,
      // Keys.scalaInstanceTopLoader.value,
    )
  }

  def makeScalaInstance(
      version: String,
      libraryJars: Array[File],
      allCompilerJars: Seq[File],
      allDocJars: Seq[File],
      state: State,
      topLoader: ClassLoader,
  ): ScalaInstance =
    import sbt.State.*
    val classLoaderCache = state.extendedClassLoaderCache
    val compilerJars = allCompilerJars.filterNot(libraryJars.contains).distinct.toArray
    val docJars = allDocJars
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
      .toArray
    val allJars = libraryJars ++ compilerJars ++ docJars

    val libraryLoader = classLoaderCache(libraryJars.toList, topLoader)
    val compilerLoader = classLoaderCache(compilerJars.toList, libraryLoader)
    val fullLoader =
      if (docJars.isEmpty) compilerLoader
      else classLoaderCache(docJars.distinct.toList, compilerLoader)
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
end Compiler
