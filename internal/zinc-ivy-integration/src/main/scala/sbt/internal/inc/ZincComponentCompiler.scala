/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.Callable

import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.io.{ Hash, IO }
import sbt.internal.librarymanagement._
import sbt.internal.util.FullLogger
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import sbt.util.{ InterfaceUtil, Logger }
import xsbti.{ ComponentProvider, GlobalLock }
import xsbti.compile.{ ClasspathOptionsUtil, CompilerBridgeProvider }

private[sbt] object ZincComponentCompiler {
  final val binSeparator = "-bin_"
  final val javaClassVersion = System.getProperty("java.class.version")

  private[inc] final val sbtOrgTemp = JsonUtil.sbtOrgTemp
  private[inc] final val modulePrefixTemp = "temp-module-"

  private final val ZincVersionPropertyFile = "/incrementalcompiler.version.properties"
  private final val ZincVersionProperty = "version"
  private[sbt] final lazy val incrementalVersion: String = {
    val cl = this.getClass.getClassLoader
    ResourceLoader.getPropertiesFor(ZincVersionPropertyFile, cl).getProperty(ZincVersionProperty)
  }

  private val CompileConf = Some(Configurations.Compile.name)
  private[sbt] def getDefaultBridgeModule(scalaVersion: String): ModuleID = {
    def compilerBridgeId(scalaVersion: String) = {
      // Defaults to bridge for 2.12 for Scala versions bigger than 2.12.x
      scalaVersion match {
        case sc if (sc startsWith "2.10.") => "compiler-bridge_2.10"
        case sc if (sc startsWith "2.11.") => "compiler-bridge_2.11"
        case _                             => "compiler-bridge_2.12"
      }
    }
    import xsbti.ArtifactInfo.SbtOrganization
    val bridgeId = compilerBridgeId(scalaVersion)
    ModuleID(SbtOrganization, bridgeId, incrementalVersion)
      .withConfigurations(CompileConf)
      .sources()
  }

  /** Defines the internal implementation of a bridge provider. */
  private class ZincCompilerBridgeProvider(
      userProvidedBridgeSources: Option[ModuleID],
      manager: ZincComponentManager,
      ivyConfiguration: IvyConfiguration,
      scalaJarsTarget: File
  ) extends CompilerBridgeProvider {

    /**
     * Defines a richer interface for Scala users that want to pass in an explicit module id.
     *
     * Note that this method cannot be defined in [[CompilerBridgeProvider]] because [[ModuleID]]
     * is a Scala-defined class to which the compiler bridge cannot depend on.
     */
    def compiledBridge(bridgeSources: ModuleID,
                       scalaInstance: xsbti.compile.ScalaInstance,
                       logger: xsbti.Logger): File = {
      val autoClasspath = ClasspathOptionsUtil.auto
      val raw = new RawCompiler(scalaInstance, autoClasspath, logger)
      val zinc = new ZincComponentCompiler(raw, manager, ivyConfiguration, bridgeSources, logger)
      logger.debug(InterfaceUtil.f0(s"Getting $bridgeSources for Scala ${scalaInstance.version}"))
      zinc.compiledBridgeJar
    }

    override def fetchCompiledBridge(scalaInstance: xsbti.compile.ScalaInstance,
                                     logger: xsbti.Logger): File = {
      val scalaVersion = scalaInstance.actualVersion()
      val bridgeSources = userProvidedBridgeSources getOrElse getDefaultBridgeModule(scalaVersion)
      compiledBridge(bridgeSources, scalaInstance, logger)
    }

    private final case class ScalaArtifacts(compiler: File, library: File, others: Vector[File])

    private def getScalaArtifacts(scalaVersion: String, logger: xsbti.Logger): ScalaArtifacts = {
      def isPrefixedWith(artifact: File, prefix: String) = artifact.getName.startsWith(prefix)

      import xsbti.ArtifactInfo._
      import UnresolvedWarning.unresolvedWarningLines
      val fullLogger = new FullLogger(logger)
      val CompileConf = Some(Configurations.Compile.name)
      val dummyModule = ModuleID(JsonUtil.sbtOrgTemp, s"tmp-scala-$scalaVersion", scalaVersion)
      val scalaLibrary = ModuleID(ScalaOrganization, ScalaLibraryID, scalaVersion)
      val scalaCompiler = ModuleID(ScalaOrganization, ScalaCompilerID, scalaVersion)
      val dependencies = Vector(scalaLibrary, scalaCompiler).map(_.withConfigurations(CompileConf))
      val wrapper = dummyModule.withConfigurations(CompileConf)
      val ivySbt: IvySbt = new IvySbt(ivyConfiguration)
      val ivyModule = ZincIvyActions.getModule(ivySbt, wrapper, dependencies)
      ZincIvyActions.update(ivyModule, scalaJarsTarget, noSource = true, fullLogger) match {
        case Left(uw) =>
          val unresolvedLines = unresolvedWarningLines.showLines(uw).mkString("\n")
          val unretrievedMessage = s"The Scala compiler and library could not be retrieved."
          throw new InvalidComponent(s"$unretrievedMessage\n$unresolvedLines")
        case Right(allArtifacts) =>
          val isScalaCompiler = (f: File) => isPrefixedWith(f, "scala-compiler-")
          val isScalaLibrary = (f: File) => isPrefixedWith(f, "scala-library-")
          val maybeScalaCompiler = allArtifacts.find(isScalaCompiler)
          val maybeScalaLibrary = allArtifacts.find(isScalaLibrary)
          val others = allArtifacts.filterNot(a => isScalaCompiler(a) || isScalaLibrary(a))
          val scalaCompiler = maybeScalaCompiler.getOrElse(throw MissingScalaJar.compiler)
          val scalaLibrary = maybeScalaLibrary.getOrElse(throw MissingScalaJar.library)
          ScalaArtifacts(scalaCompiler, scalaLibrary, others)
      }
    }

    override def fetchScalaInstance(scalaVersion: String,
                                    logger: xsbti.Logger): xsbti.compile.ScalaInstance = {
      import sbt.io.Path.toURLs
      val scalaArtifacts = getScalaArtifacts(scalaVersion, logger)
      val scalaCompiler = scalaArtifacts.compiler
      val scalaLibrary = scalaArtifacts.library
      val jarsToLoad = (scalaCompiler +: scalaLibrary +: scalaArtifacts.others).toArray
      assert(jarsToLoad.forall(_.exists), "One or more jar(s) in the Scala instance do not exist.")
      val loader = new URLClassLoader(toURLs(jarsToLoad), ClasspathUtilities.rootLoader)
      val properties = ResourceLoader.getSafePropertiesFor("compiler.properties", loader)
      val loaderVersion = Option(properties.getProperty("version.number"))
      val scalaV = loaderVersion.getOrElse("unknown")
      new ScalaInstance(scalaV, loader, scalaLibrary, scalaCompiler, jarsToLoad, loaderVersion)
    }
  }

  // Used by ZincUtil.
  def interfaceProvider(compilerBridgeSource: ModuleID,
                        manager: ZincComponentManager,
                        ivyConfiguration: IvyConfiguration,
                        scalaJarsTarget: File): CompilerBridgeProvider =
    new ZincCompilerBridgeProvider(Some(compilerBridgeSource),
                                   manager,
                                   ivyConfiguration,
                                   scalaJarsTarget)

  def interfaceProvider(manager: ZincComponentManager,
                        ivyConfiguration: IvyConfiguration,
                        scalaJarsTarget: File): CompilerBridgeProvider =
    new ZincCompilerBridgeProvider(None, manager, ivyConfiguration, scalaJarsTarget)

  private final val LocalIvy = s"$${user.home}/.ivy2/local/${Resolver.localBasePattern}"
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
    import sbt.io.syntax._
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

  def getDefaultConfiguration(baseDirectory: File,
                              ivyHome: File,
                              resolvers0: Array[Resolver],
                              log: xsbti.Logger): IvyConfiguration = {
    import sbt.io.syntax._
    val resolvers = resolvers0.toVector
    val chainResolver = ChainedResolver("zinc-chain", resolvers)
    new InlineIvyConfiguration(
      paths = IvyPaths(baseDirectory, Some(ivyHome)),
      resolvers = resolvers,
      otherResolvers = Vector.empty,
      moduleConfigurations = Vector(ModuleConfiguration("*", chainResolver)),
      lock = None,
      checksums = Vector.empty,
      managedChecksums = false,
      resolutionCacheDir = Some(ivyHome / "resolution-cache"),
      updateOptions = UpdateOptions(),
      log = log
    )
  }
}

/**
 * Component compiler which is able to to retrieve the compiler bridge sources
 * `sourceModule` using Ivy.
 * The compiled classes are cached using the provided component manager according
 * to the actualVersion field of the RawCompiler.
 */
private[inc] class ZincComponentCompiler(
    compiler: RawCompiler,
    manager: ZincComponentManager,
    ivyConfiguration: IvyConfiguration,
    bridgeSources: ModuleID,
    log: Logger
) {
  import sbt.internal.util.{ BufferedLogger, FullLogger }
  private final val ivySbt: IvySbt = new IvySbt(ivyConfiguration)
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
   * Example: "org.scala-sbt-compiler-bridge-1.0.0-bin_2.11.7__50.0".
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
   * Returns an ivy module that will wrap and download a given `moduleID`.
   *
   * @param moduleID The `moduleID` that needs to be wrapped in a dummy module to be downloaded.
   */
  private[inc] def wrapDependencyInModule(moduleID: ModuleID): IvySbt#Module = {
    import ZincComponentCompiler.{ sbtOrgTemp, modulePrefixTemp }
    val sha1 = Hash.toHex(Hash(moduleID.name))
    val dummyID = ModuleID(sbtOrgTemp, s"$modulePrefixTemp$sha1", moduleID.revision)
      .withConfigurations(moduleID.configurations)
    ZincIvyActions.getModule(ivySbt, dummyID, Vector(moduleID))
  }

  /**
   * Resolves the compiler bridge sources, compiles them and installs the sbt component
   * in the local filesystem to make sure that it's reused the next time is required.
   *
   * @param compilerBridgeId The identifier for the compiler bridge sources.
   */
  private def compileAndInstall(compilerBridgeId: String): Unit = {
    import UnresolvedWarning.unresolvedWarningLines
    val ivyModuleForBridge = wrapDependencyInModule(bridgeSources)
    IO.withTemporaryDirectory { binaryDirectory =>
      val target = new File(binaryDirectory, s"$compilerBridgeId.jar")
      buffered bufferQuietly {
        IO.withTemporaryDirectory { retrieveDirectory =>
          ZincIvyActions.update(ivyModuleForBridge, retrieveDirectory, false, buffered) match {
            case Left(uw) =>
              val mod = bridgeSources.toString
              val unresolvedLines = unresolvedWarningLines.showLines(uw).mkString("\n")
              val unretrievedMessage = s"The compiler bridge sources $mod could not be retrieved."
              throw new InvalidComponent(s"$unretrievedMessage\n$unresolvedLines")

            case Right(allArtifacts) =>
              val (srcs, xsbtiJars) = allArtifacts.partition(_.getName.endsWith("-sources.jar"))
              val toCompileID = bridgeSources.name
              AnalyzingCompiler.compileSources(srcs, target, xsbtiJars, toCompileID, compiler, log)
              manager.define(compilerBridgeId, Seq(target))
          }
        }

      }
    }
  }

}

object ZincIvyActions {
  type IvyModule = IvySbt#Module

  /** Define the default configurations for a Zinc module wrapper. */
  private final val DefaultConfigurations: Vector[Configuration] =
    Vector(Configurations.Component, Configurations.Compile)

  /**
   * Create a module from its dummy wrapper and its dependencies.
   *
   * @param wrapper The ModuleID wrapper that will download the dependencies.
   * @param deps The dependencies to be downloaded.
   * @return A fully-fledged ivy module to be used for [[IvyActions]].
   */
  private[inc] def getModule(ivySbt: IvySbt,
                             wrapper: ModuleID,
                             deps: Vector[ModuleID]): IvyModule = {
    val moduleInfo = ModuleInfo(wrapper.name)
    val componentIvySettings = InlineConfiguration(
      validate = false,
      ivyScala = None,
      module = wrapper,
      moduleInfo = moduleInfo,
      dependencies = deps
    ).withConfigurations(DefaultConfigurations)
    new ivySbt.Module(componentIvySettings)
  }

  // The implementation of this method is linked to `wrapDependencyInModule`
  private def prettyPrintDependency(module: IvyModule): String = {
    module.moduleSettings match {
      case ic: InlineConfiguration =>
        // Pretty print the module as `ModuleIDExtra.toStringImpl` does.
        ic.dependencies.map(m => s"${m.organization}:${m.name}:${m.revision}").mkString(", ")
      case _ => sys.error("Fatal: configuration to download was not inline.")
    }
  }

  private final val warningConf = UnresolvedWarningConfiguration()
  private final val defaultRetrievePattern = Resolver.defaultRetrievePattern
  private def defaultUpdateConfiguration(targetDir: File, noSource: Boolean): UpdateConfiguration = {
    val retrieve = RetrieveConfiguration(targetDir, defaultRetrievePattern, false, None)
    val logLevel = UpdateLogging.DownloadOnly
    val defaultExcluded = Set("doc")
    val finalExcluded = if (noSource) defaultExcluded + "src" else defaultExcluded
    val artifactFilter = ArtifactTypeFilter.forbid(finalExcluded)
    UpdateConfiguration(retrieve = Some(retrieve),
                        missingOk = false,
                        logging = logLevel,
                        artifactFilter = artifactFilter,
                        offline = false,
                        frozen = false)
  }

  private[inc] def update(module: IvyModule,
                          retrieveDirectory: File,
                          noSource: Boolean = false,
                          logger: Logger): Either[UnresolvedWarning, Vector[File]] = {
    import IvyActions.updateEither
    val updateConfiguration = defaultUpdateConfiguration(retrieveDirectory, noSource)
    val dependencies = prettyPrintDependency(module)
    logger.info(s"Attempting to fetch $dependencies.")
    val clockForCache = LogicalClock.unknown
    updateEither(module, updateConfiguration, warningConf, clockForCache, None, logger) match {
      case Left(unresolvedWarning) =>
        logger.debug(s"Couldn't retrieve module(s) ${prettyPrintDependency(module)}.")
        Left(unresolvedWarning)

      case Right(updateReport) =>
        val allFiles = updateReport.allFiles
        logger.debug(s"Files retrieved for ${prettyPrintDependency(module)}:")
        logger.debug(allFiles mkString ", ")
        Right(allFiles)
    }
  }
}
