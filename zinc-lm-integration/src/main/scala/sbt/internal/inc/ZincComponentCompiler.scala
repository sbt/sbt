/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package inc

import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.librarymanagement.JsonUtil
import sbt.internal.util.{ BufferedLogger, FullLogger }
import sbt.io.IO
import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import sbt.util.InterfaceUtil.{ toSupplier as f0 }
import xsbti.ArtifactInfo.*
import xsbti.compile.{
  ClasspathOptionsUtil,
  CompilerBridgeProvider,
  ScalaInstance as XScalaInstance
}
import xsbti.{ ComponentProvider, GlobalLock, Logger }

import java.io.File
import java.nio.file.{ Files, Path }
import java.util.concurrent.Callable

private[sbt] object ZincComponentCompiler {
  final val binSeparator = "-bin_"
  final val javaClassVersion = System.getProperty("java.class.version")

  private[inc] final val sbtOrgTemp = JsonUtil.sbtOrgTemp
  private[inc] final val modulePrefixTemp = "temp-module-"

  private val lock: AnyRef = new {}

  /** Defines the internal implementation of a bridge provider. */
  private class ZincCompilerBridgeProvider(
      userProvidedBridgeSources: Option[ModuleID],
      manager: ZincComponentManager,
      dependencyResolution: DependencyResolution,
      scalaJarsTarget: File
  ) extends CompilerBridgeProvider {

    /**
     * Defines a richer interface for Scala users that want to pass in an explicit module id.
     *
     * Note that this method cannot be defined in [[CompilerBridgeProvider]] because [[ModuleID]]
     * is a Scala-defined class to which the compiler bridge cannot depend on.
     */
    def compiledBridge(
        bridgeSources: ModuleID,
        scalaInstance: XScalaInstance,
        logger: Logger,
    ): File = lock.synchronized {
      val raw = new RawCompiler(
        scalaInstance,
        ClasspathOptionsUtil.autoNoboot(scalaInstance.version),
        logger
      )
      val zinc =
        new ZincComponentCompiler(raw, manager, dependencyResolution, bridgeSources, logger)
      logger.debug(f0(s"Getting $bridgeSources for Scala ${scalaInstance.version}"))
      zinc.compiledBridgeJar
    }

    override def fetchCompiledBridge(scalaInstance: XScalaInstance, logger: Logger): File = {
      val scalaVersion = scalaInstance.actualVersion()
      val bridgeSources = userProvidedBridgeSources
        .getOrElse(ZincLmUtil.getDefaultBridgeSourceModule(scalaVersion))
      compiledBridge(bridgeSources, scalaInstance, logger)
    }

    // internal representation of Scala artifacts
    private case class ScalaArtifacts(
        compilerJar: Path,
        libraryJars: Vector[Path],
        others: Vector[Path]
    )

    private def getScalaArtifacts(scalaVersion: String, logger: Logger): ScalaArtifacts = {
      def isPrefixedWith(artifact: Path, prefix: String) =
        artifact.getFileName.toString.startsWith(prefix)

      val fullLogger = new FullLogger(logger)
      val CompileConf = Some(Configurations.Compile.name)
      val dummyModule = ModuleID(JsonUtil.sbtOrgTemp, s"tmp-scala-$scalaVersion", scalaVersion)
      val scalaLibrary = ModuleID(ScalaOrganization, ScalaLibraryID, scalaVersion)
      val scalaCompiler = ModuleID(ScalaOrganization, ScalaCompilerID, scalaVersion)
      val dependencies = Vector(scalaLibrary, scalaCompiler).map(_.withConfigurations(CompileConf))
      val wrapper = dummyModule.withConfigurations(CompileConf)
      val moduleDescriptorConfiguration =
        ModuleDescriptorConfiguration(wrapper, ModuleInfo(wrapper.name))
          .withDependencies(dependencies)
          .withConfigurations(ZincLMHelper.DefaultConfigurations)

      val moduleDescriptor = dependencyResolution.moduleDescriptor(moduleDescriptorConfiguration)
      val allArtifacts = ZincLMHelper.update(
        dependencyResolution,
        moduleDescriptor,
        scalaJarsTarget,
        noSource = true,
        fullLogger,
        "Scala compiler and library",
      )
      val isScalaCompiler = (f: Path) => isPrefixedWith(f, "scala-compiler-")
      val isScalaLibrary = (f: Path) => isPrefixedWith(f, "scala-library-")
      val maybeScalaCompiler = allArtifacts.find(isScalaCompiler)
      val maybeScalaLibrary = allArtifacts.find(isScalaLibrary)
      val others = allArtifacts.filterNot(a => isScalaCompiler(a) || isScalaLibrary(a))
      val scalaCompilerJar = maybeScalaCompiler.getOrElse(throw MissingScalaJar.compiler)
      val scalaLibraryJar = maybeScalaLibrary.getOrElse(throw MissingScalaJar.library)
      ScalaArtifacts(scalaCompilerJar, Vector(scalaLibraryJar), others)
    }

    override def fetchScalaInstance(scalaVersion: String, logger: Logger): XScalaInstance = {
      val scalaArtifacts = getScalaArtifacts(scalaVersion, logger)
      val scalaCompilerJar = scalaArtifacts.compilerJar
      val scalaLibraryJars = scalaArtifacts.libraryJars
      val jarsToLoad: Vector[Path] =
        (Vector(scalaCompilerJar) ++ scalaLibraryJars ++ scalaArtifacts.others)
      assert(
        jarsToLoad.forall(Files.exists(_)),
        "One or more jar(s) in the Scala instance do not exist."
      )
      val loaderLibraryOnly = ClasspathUtil.toLoader(scalaLibraryJars)
      val jarsToLoad2 = jarsToLoad diff scalaLibraryJars
      val loader = ClasspathUtil.toLoader(jarsToLoad2, loaderLibraryOnly)
      val properties = ResourceLoader.getSafePropertiesFor("compiler.properties", loader)
      val loaderVersion = Option(properties.getProperty("version.number"))
      val scalaV = loaderVersion.getOrElse("unknown")
      val allJars = jarsToLoad.map(_.toFile).toArray
      new ScalaInstance(
        scalaV,
        loader,
        loader,
        loaderLibraryOnly,
        scalaLibraryJars.map(_.toFile).toArray,
        allJars,
        allJars,
        loaderVersion,
      )
    }
  }

  // Used by ZincUtil.
  def interfaceProvider(
      compilerBridgeSource: ModuleID,
      manager: ZincComponentManager,
      dependencyResolution: DependencyResolution,
      scalaJarsTarget: File,
  ): CompilerBridgeProvider = {
    val bridgeSources = Some(compilerBridgeSource)
    new ZincCompilerBridgeProvider(bridgeSources, manager, dependencyResolution, scalaJarsTarget)
  }

  def interfaceProvider(
      manager: ZincComponentManager,
      dependencyResolution: DependencyResolution,
      scalaJarsTarget: File,
  ): CompilerBridgeProvider =
    new ZincCompilerBridgeProvider(None, manager, dependencyResolution, scalaJarsTarget)

  private final val LocalIvy =
    (sys.props.get("sbt.ivy.home") match {
      case Some(home) =>
        if (home.endsWith("/")) home
        else home + "/"
      case _ => s"$${user.home}/.ivy2/"
    }) + "local/" + Resolver.localBasePattern

  final val LocalResolver: Resolver = {
    val toUse = Vector(LocalIvy)
    val ivyPatterns = Patterns().withIsMavenCompatible(false)
    val finalPatterns = ivyPatterns.withIvyPatterns(toUse).withArtifactPatterns(toUse)
    FileRepository("local", Resolver.defaultFileConfiguration, finalPatterns)
  }

  def getDefaultLock: GlobalLock = new GlobalLock {
    override def apply[T](file: File, callable: Callable[T]): T = callable.call()
  }

  /** Defines a default component provider that manages the component in a given directory. */
  private final class DefaultComponentProvider(targetDir: File) extends ComponentProvider {
    import sbt.io.syntax.*
    private val LockFile = targetDir / "lock"
    override def lockFile(): File = LockFile
    override def componentLocation(id: String): File = targetDir / id
    override def component(componentID: String): Array[File] =
      IO.listFiles(targetDir / componentID)
    override def defineComponent(componentID: String, files: Array[File]): Unit =
      files.foreach(f => IO.copyFile(f, targetDir / componentID / f.getName))
    override def addToComponent(componentID: String, files: Array[File]): Boolean = {
      defineComponent(componentID, files)
      true
    }
  }

  def getDefaultComponentProvider(targetDir: File): ComponentProvider = {
    require(targetDir.isDirectory)
    new DefaultComponentProvider(targetDir)
  }

}

/**
 * Component compiler which is able to to retrieve the compiler bridge sources
 * `sourceModule` using a `DependencyResolution` instance.
 * The compiled classes are cached using the provided component manager according
 * to the actualVersion field of the RawCompiler.
 */
private[inc] class ZincComponentCompiler(
    compiler: RawCompiler,
    manager: ZincComponentManager,
    dependencyResolution: DependencyResolution,
    bridgeSources: ModuleID,
    log: sbt.util.Logger
) {
  private final val buffered = new BufferedLogger(FullLogger(log))

  def compiledBridgeJar: File = {
    val jarBinaryName = createBridgeSourcesID(bridgeSources)
    manager.file(jarBinaryName)(IfMissing.define(true, compileAndInstall(jarBinaryName)))
  }

  /**
   * Returns the id for the compiler interface component.
   *
   * The ID contains the following parts:
   *   - The organization, name and revision.
   *   - The bin separator to make clear the jar represents binaries.
   *   - The Scala version for which the compiler interface is meant to.
   *   - The JVM class version.
   *
   * Example: `"org.scala-sbt-compiler-bridge-1.0.0-bin_2.11.7__50.0"`.
   *
   * @param sources The moduleID representing the compiler bridge sources.
   * @return The complete jar identifier for the bridge sources.
   */
  private def createBridgeSourcesID(sources: ModuleID): String = {
    import ZincComponentCompiler.{ binSeparator, javaClassVersion }
    val id = s"${sources.organization}-${sources.name}-${sources.revision}"
    val scalaVersion = compiler.scalaInstance.actualVersion()
    s"$id$binSeparator${scalaVersion}__$javaClassVersion"
  }

  /**
   * Resolves the compiler bridge sources, compiles them and installs the sbt component
   * in the local filesystem to make sure that it's reused the next time is required.
   *
   * @param compilerBridgeId The identifier for the compiler bridge sources.
   */
  private def compileAndInstall(compilerBridgeId: String): Unit = {
    val moduleForBridge = dependencyResolution.wrapDependencyInModule(bridgeSources)
    IO.withTemporaryDirectory { binaryDirectory =>
      val target = new File(binaryDirectory, s"$compilerBridgeId.jar")
      buffered.bufferQuietly {
        IO.withTemporaryDirectory { retrieveDirectory =>
          val allArtifacts = ZincLMHelper.update(
            dependencyResolution,
            moduleForBridge,
            retrieveDirectory,
            noSource = false,
            buffered,
            s"compiler bridge sources $moduleForBridge",
          )
          val (srcs, xsbtiJars) =
            allArtifacts.partition(_.getFileName.toString.endsWith("-sources.jar"))
          val toCompileID = bridgeSources.name
          AnalyzingCompiler.compileSources(
            srcs,
            target.toPath,
            xsbtiJars,
            toCompileID,
            compiler,
            log
          )
          manager.define(compilerBridgeId, Seq(target))
        }
      }
    }
  }

}

private object ZincLMHelper {
  private final val warningConf = UnresolvedWarningConfiguration()
  private final val defaultRetrievePattern = Resolver.defaultRetrievePattern

  private[inc] final val DefaultConfigurations: Vector[Configuration] =
    Vector(Configurations.Component, Configurations.Compile)

  private[inc] def update(
      dependencyResolution: DependencyResolution,
      module: ModuleDescriptor,
      retrieveDirectory: File,
      noSource: Boolean,
      logger: sbt.util.Logger,
      desc: String,
  ): Vector[Path] = {
    val updateConfiguration = newUpdateConfiguration(retrieveDirectory, noSource)
    val dependencies = prettyPrintDependency(module)
    logger.info(s"Attempting to fetch $dependencies.")
    dependencyResolution.update(module, updateConfiguration, warningConf, logger) match {
      case Left(uw) =>
        logger.debug(s"couldn't retrieve module(s) ${prettyPrintDependency(module)}.")
        val unretrievedMessage = s"The $desc could not be retrieved."
        val unresolvedLines = UnresolvedWarning.unresolvedWarningLines.showLines(uw).mkString("\n")
        throw new InvalidComponent(s"$unretrievedMessage\n$unresolvedLines")
      case Right(updateReport) =>
        val allFiles = updateReport.allFiles.map(_.toPath)
        logger.debug(s"Files retrieved for ${prettyPrintDependency(module)}:")
        logger.debug(allFiles.mkString(", "))
        allFiles
    }
  }

  private def newUpdateConfiguration(targetDir: File, noSource: Boolean): UpdateConfiguration = {
    val retrieve = RetrieveConfiguration()
      .withRetrieveDirectory(targetDir)
      .withOutputPattern(defaultRetrievePattern)
    val logLevel = UpdateLogging.DownloadOnly
    val defaultExcluded = Set("doc")
    val finalExcluded = if (noSource) defaultExcluded + "src" else defaultExcluded
    val artifactFilter = ArtifactTypeFilter.forbid(finalExcluded)
    UpdateConfiguration()
      .withRetrieveManaged(retrieve)
      .withLogging(logLevel)
      .withArtifactFilter(artifactFilter)
  }

  private def prettyPrintDependency(module: ModuleDescriptor): String = {
    module.directDependencies
      .map(m => s"${m.organization}:${m.name}:${m.revision}") // like ModuleIDExtra.toStringImpl
      .mkString(", ")
  }
}
