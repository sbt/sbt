package sbt.internal.inc

import java.io.File
import java.net.URLClassLoader
import java.util.Properties
import java.util.concurrent.Callable

import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.internal.librarymanagement.{ ComponentManager, IvySbt, BaseIvySpecification }
import sbt.io.IO
import sbt.io.Path._
import sbt.librarymanagement.{ ModuleID, UpdateOptions, Resolver }
import sbt.util.Logger
import xsbti.{ ComponentProvider, GlobalLock }

abstract class BridgeProviderSpecification extends BaseIvySpecification {

  override def resolvers: Seq[Resolver] = Seq(Resolver.mavenLocal, Resolver.jcenterRepo)
  val ivyConfiguration = mkIvyConfiguration(UpdateOptions())
  val ivySbt = new IvySbt(ivyConfiguration)

  val home = new File(sys.props("user.home"))
  val ivyCache = home / ".ivy2" / "cache"

  def getCompilerBridge(tempDir: File, log: Logger, scalaVersion: String): File = {
    val instance = scalaInstanceFromFile(scalaVersion)
    val bridgeId = compilerBridgeId(scalaVersion)
    val sourceModule = ModuleID(xsbti.ArtifactInfo.SbtOrganization, bridgeId, ComponentCompiler.incrementalVersion, Some("component")).sources()

    val raw = new RawCompiler(instance, ClasspathOptions.auto, log)
    val manager = new ComponentManager(lock, provider(tempDir), None, log)
    val componentCompiler = new IvyComponentCompiler(raw, manager, ivyConfiguration, sourceModule, log)

    val bridge = componentCompiler.apply()
    val target = tempDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  }

  def scalaInstanceFromFile(scalaVersion: String): ScalaInstance =
    scalaInstance(
      ivyCache / "org.scala-lang" / "scala-compiler" / "jars" / s"scala-compiler-$scalaVersion.jar",
      ivyCache / "org.scala-lang" / "scala-library" / "jars" / s"scala-library-$scalaVersion.jar",
      Seq(ivyCache / "org.scala-lang" / "scala-reflect" / "jars" / s"scala-reflect-$scalaVersion.jar")
    )

  def scalaInstance(scalaCompiler: File, scalaLibrary: File, scalaExtra: Seq[File]): ScalaInstance = {
    val loader = scalaLoader(scalaLibrary +: scalaCompiler +: scalaExtra)
    val version = scalaVersion(loader)
    val allJars = (scalaLibrary +: scalaCompiler +: scalaExtra).toArray
    new ScalaInstance(version.getOrElse("unknown"), loader, scalaLibrary, scalaCompiler, allJars, version)
  }

  def compilerBridgeId(scalaVersion: String) =
    scalaVersion match {
      case sc if sc startsWith "2.11" => "compiler-bridge_2.11"
      case _                          => "compiler-bridge_2.10"
    }

  def scalaLoader(jars: Seq[File]) = new URLClassLoader(sbt.io.Path.toURLs(jars), ClasspathUtilities.rootLoader)
  def scalaVersion(scalaLoader: ClassLoader): Option[String] =
    propertyFromResource("compiler.properties", "version.number", scalaLoader)

  /**
   * Get a property from a properties file resource in the classloader.
   */
  def propertyFromResource(resource: String, property: String, classLoader: ClassLoader): Option[String] = {
    val props = propertiesFromResource(resource, classLoader)
    Option(props.getProperty(property))
  }

  /**
   * Get all properties from a properties file resource in the classloader.
   */
  def propertiesFromResource(resource: String, classLoader: ClassLoader): Properties = {
    val props = new Properties
    val stream = classLoader.getResourceAsStream(resource)
    try { props.load(stream) }
    catch { case _: Exception => }
    finally { if (stream ne null) stream.close() }
    props
  }

  private val lock: GlobalLock = new GlobalLock {
    override def apply[T](file: File, callable: Callable[T]): T = callable.call()
  }

  private def provider(targetDir: File): ComponentProvider = new ComponentProvider {

    override def lockFile(): File = targetDir / "lock"

    override def defineComponent(componentID: String, files: Array[File]): Unit =
      files foreach { f => IO.copyFile(f, targetDir / componentID / f.getName) }

    override def addToComponent(componentID: String, files: Array[File]): Boolean = {
      defineComponent(componentID, files)
      true
    }

    override def component(componentID: String): Array[File] =
      IO.listFiles(targetDir / componentID)

    override def componentLocation(id: String): File = throw new UnsupportedOperationException
  }

}
