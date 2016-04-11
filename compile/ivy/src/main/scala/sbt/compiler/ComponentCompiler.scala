/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt
package compiler

import java.io.File
import scala.util.Try

object ComponentCompiler {
  val xsbtiID = "xsbti"
  val srcExtension = "-src"
  val binSeparator = "-bin_"
  val compilerInterfaceID = "compiler-interface"
  val compilerInterfaceSrcID = compilerInterfaceID + srcExtension
  val javaVersion = System.getProperty("java.class.version")

  @deprecated("Use `interfaceProvider(ComponentManager, CompilerBridgeProvider)`.", "0.13.12")
  def interfaceProvider(manager: ComponentManager): CompilerInterfaceProvider = new CompilerInterfaceProvider {
    def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File =
      {
        // this is the instance used to compile the interface component
        val componentCompiler = new ComponentCompiler(new RawCompiler(scalaInstance, ClasspathOptions.auto, log), manager)
        log.debug("Getting " + compilerInterfaceID + " from component compiler for Scala " + scalaInstance.version)
        componentCompiler(compilerInterfaceID)
      }
  }

  @deprecated("Use `interfaceProvider(ComponentManager, CompilerBridgeProvider)`", "0.13.12")
  def interfaceProvider(manager: ComponentManager, ivyConfiguration: IvyConfiguration, sourcesModule: ModuleID): CompilerInterfaceProvider =
    interfaceProvider(manager, IvyBridgeProvider(ivyConfiguration, sourcesModule))

  def interfaceProvider(manager: ComponentManager, compilerBridgeProvider: CompilerBridgeProvider): CompilerInterfaceProvider = new CompilerInterfaceProvider {
    def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File =
      compilerBridgeProvider match {
        case IvyBridgeProvider(ivyConfiguration, sourcesModule) =>
          val componentCompiler = new IvyComponentCompiler(new RawCompiler(scalaInstance, ClasspathOptions.auto, log), manager, ivyConfiguration, sourcesModule, log)
          log.debug("Getting " + sourcesModule + " from component compiler for Scala " + scalaInstance.version)
          componentCompiler()
        case ResourceBridgeProvider(sourceJarName) =>
          val componentCompiler = new ResourceComponentCompiler(new RawCompiler(scalaInstance, ClasspathOptions.auto, log), manager, sourceJarName, log)
          log.debug("Compiling bridge source from resources for Scala " + scalaInstance.version)
          componentCompiler()
      }
  }
}
/**
 * This class provides source components compiled with the provided RawCompiler.
 * The compiled classes are cached using the provided component manager according
 * to the actualVersion field of the RawCompiler.
 */
@deprecated("Replaced by IvyComponentCompiler and ResourceComponentCompiler.", "0.13.12")
class ComponentCompiler(compiler: RawCompiler, manager: ComponentManager) {
  import ComponentCompiler._
  def apply(id: String): File =
    try { getPrecompiled(id) }
    catch { case _: InvalidComponent => getLocallyCompiled(id) }

  /**
   * Gets the precompiled (distributed with sbt) component with the given 'id'
   * If the component has not been precompiled, this throws InvalidComponent.
   */
  def getPrecompiled(id: String): File = manager.file(binaryID(id, false))(IfMissing.Fail)
  /**
   * Get the locally compiled component with the given 'id' or compiles it if it has not been compiled yet.
   * If the component does not exist, this throws InvalidComponent.
   */
  def getLocallyCompiled(id: String): File =
    {
      val binID = binaryID(id, true)
      manager.file(binID)(new IfMissing.Define(true, compileAndInstall(id, binID)))
    }
  def clearCache(id: String): Unit = manager.clearCache(binaryID(id, true))
  protected def binaryID(id: String, withJavaVersion: Boolean) =
    {
      val base = id + binSeparator + compiler.scalaInstance.actualVersion
      if (withJavaVersion) base + "__" + javaVersion else base
    }
  protected def compileAndInstall(id: String, binID: String): Unit = {
    val srcID = id + srcExtension
    IO.withTemporaryDirectory { binaryDirectory =>
      val targetJar = new File(binaryDirectory, id + ".jar")
      val xsbtiJars = manager.files(xsbtiID)(IfMissing.Fail)
      AnalyzingCompiler.compileSources(manager.files(srcID)(IfMissing.Fail), targetJar, xsbtiJars, id, compiler, manager.log)
      manager.define(binID, Seq(targetJar))
    }
  }
}

/**
 * Compiles the compiler bridge using the source extracted from the resources on classpath.
 */
private[compiler] class ResourceComponentCompiler(compiler: RawCompiler, manager: ComponentManager, sourceJarName: String, log: Logger) {
  import ComponentCompiler._

  def apply(): File = {
    val binID = "bridge-from-resource" + binSeparator + compiler.scalaInstance.actualVersion + "__" + javaVersion
    manager.file(binID)(new IfMissing.Define(true, compileAndInstall(binID)))
  }

  private def copyFromResources(destinationDirectory: File, fileName: String): File = {
    Option(getClass.getClassLoader.getResourceAsStream(sourceJarName)) match {
      case Some(stream) =>
        val copiedFile = new File(destinationDirectory, fileName)
        val out = new java.io.FileOutputStream(copiedFile)

        var read = 0
        val content = new Array[Byte](1024)
        while ({ read = stream.read(content); read != -1 }) {
          out.write(content, 0, read)
        }

        copiedFile

      case None =>
        throw new InvalidComponent(s"Could not find '$fileName' on resources path.")

    }
  }

  private def compileAndInstall(binID: String): Unit =
    IO.withTemporaryDirectory { binaryDirectory =>
      val targetJar = new File(binaryDirectory, s"$binID.jar")
      val xsbtiJars = manager.files(xsbtiID)(IfMissing.Fail)

      IO.withTemporaryDirectory { tempDirectory =>

        val sourceJar = copyFromResources(tempDirectory, sourceJarName)
        AnalyzingCompiler.compileSources(Seq(sourceJar), targetJar, xsbtiJars, "bridge-from-resources", compiler, log)
        manager.define(binID, Seq(targetJar))

      }

    }
}

/**
 * Component compiler which is able to to retrieve the compiler bridge sources
 * `sourceModule` using Ivy.
 * The compiled classes are cached using the provided component manager according
 * to the actualVersion field of the RawCompiler.
 */
private[compiler] class IvyComponentCompiler(compiler: RawCompiler, manager: ComponentManager, ivyConfiguration: IvyConfiguration, sourcesModule: ModuleID, log: Logger) {
  import ComponentCompiler._

  private val sbtOrg = xsbti.ArtifactInfo.SbtOrganization
  private val sbtOrgTemp = JsonUtil.sbtOrgTemp
  private val modulePrefixTemp = "temp-module-"
  private val ivySbt: IvySbt = new IvySbt(ivyConfiguration)
  private val sbtVersion = ComponentManager.version
  private val buffered = new BufferedLogger(FullLogger(log))

  def apply(): File = {
    // binID is of the form "org.example-compilerbridge-1.0.0-bin_2.11.7__50.0"
    val binID = binaryID(s"${sourcesModule.organization}-${sourcesModule.name}-${sourcesModule.revision}")
    manager.file(binID)(new IfMissing.Define(true, compileAndInstall(binID)))
  }

  private def binaryID(id: String): String = {
    val base = id + binSeparator + compiler.scalaInstance.actualVersion
    base + "__" + javaVersion
  }

  private def compileAndInstall(binID: String): Unit =
    IO.withTemporaryDirectory { binaryDirectory =>

      val targetJar = new File(binaryDirectory, s"$binID.jar")
      val xsbtiJars = manager.files(xsbtiID)(IfMissing.Fail)

      buffered bufferQuietly {

        IO.withTemporaryDirectory { retrieveDirectory =>
          (update(getModule(sourcesModule), retrieveDirectory)(_.getName endsWith "-sources.jar")) match {
            case Some(sources) =>
              AnalyzingCompiler.compileSources(sources, targetJar, xsbtiJars, sourcesModule.name, compiler, log)
              manager.define(binID, Seq(targetJar))

            case None =>
              throw new InvalidComponent(s"Couldn't retrieve source module: $sourcesModule")
          }
        }

      }
    }

  /**
   * Returns a dummy module that depends on `moduleID`.
   * Note: Sbt's implementation of Ivy requires us to do this, because only the dependencies
   *       of the specified module will be downloaded.
   */
  private def getModule(moduleID: ModuleID): ivySbt.Module = {
    val sha1 = Hash.toHex(Hash(moduleID.name))
    val dummyID = ModuleID(sbtOrgTemp, modulePrefixTemp + sha1, moduleID.revision, moduleID.configurations)
    getModule(dummyID, Seq(moduleID))
  }

  private def getModule(moduleID: ModuleID, deps: Seq[ModuleID], uo: UpdateOptions = UpdateOptions()): ivySbt.Module = {
    val moduleSetting = InlineConfiguration(
      module = moduleID,
      moduleInfo = ModuleInfo(moduleID.name),
      dependencies = deps,
      configurations = Seq(Configurations.Component),
      ivyScala = None)

    new ivySbt.Module(moduleSetting)
  }

  private def dependenciesNames(module: ivySbt.Module): String = module.moduleSettings match {
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

  private def update(module: ivySbt.Module, retrieveDirectory: File)(predicate: File => Boolean): Option[Seq[File]] = {

    val retrieveConfiguration = new RetrieveConfiguration(retrieveDirectory, Resolver.defaultRetrievePattern, false)
    val updateConfiguration = new UpdateConfiguration(Some(retrieveConfiguration), true, UpdateLogging.DownloadOnly)

    buffered.info(s"Attempting to fetch ${dependenciesNames(module)}. This operation may fail.")
    IvyActions.updateEither(module, updateConfiguration, UnresolvedWarningConfiguration(), LogicalClock.unknown, None, buffered) match {
      case Left(unresolvedWarning) =>
        buffered.debug("Couldn't retrieve module ${dependenciesNames(module)}.")
        None

      case Right(updateReport) =>
        val allFiles =
          for {
            conf <- updateReport.configurations
            m <- conf.modules
            (_, f) <- m.artifacts
          } yield f

        buffered.debug(s"Files retrieved for ${dependenciesNames(module)}:")
        buffered.debug(allFiles mkString ", ")

        allFiles filter predicate match {
          case Seq() => None
          case files => Some(files)
        }

    }
  }
}
