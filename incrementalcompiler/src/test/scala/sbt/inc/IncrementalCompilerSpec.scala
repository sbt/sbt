package sbt
package inc

import java.io.File

import sbt.io.IO
import sbt.util.Logger
import sbt.internal.util.UnitSpec
import sbt.internal.inc.{ AnalyzingCompiler, IncrementalCompilerImpl, ScalaInstance, RawCompiler, ClasspathOptions, ComponentCompiler }
import sbt.internal.inc.{ CompilerInterfaceProvider, CompilerCache, LoggerReporter }
import sbt.internal.librarymanagement.{ BaseIvySpecification, IvySbt, InlineIvyConfiguration }
import sbt.librarymanagement.{ ModuleID, UpdateOptions, Resolver }
import java.net.URLClassLoader
import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.Maybe
import xsbti.compile.{ F1, CompileAnalysis, DefinesClass, IncOptionsUtil, PreviousResult, MiniSetup, CompileOrder }
import sbt.io.Path._

class IncrementalCompilerSpec extends BaseIvySpecification {
  override def resolvers: Seq[Resolver] = super.resolvers ++ Seq(Resolver.mavenLocal, Resolver.jcenterRepo)
  val ivyConfiguration = mkIvyConfiguration(UpdateOptions())
  val ivySbt = new IvySbt(ivyConfiguration)

  val home = new File(sys.props("user.home"))
  val scalaVersion = scala.util.Properties.versionNumberString
  val ivyCache = home / ".ivy2" / "cache"
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val CompilerBridgeId =
    scalaVersion match {
      case sc if sc startsWith "2.11" => "compiler-bridge_2.11"
      case sc if sc startsWith "2.10" => "compiler-bridge_2.10"
    }
  val JavaClassVersion = System.getProperty("java.class.version")
  val maxErrors = 100
  val knownSampleGoodFile =
    new File(classOf[IncrementalCompilerSpec].getResource("Good.scala").toURI)

  "incremental compiler" should "compile" in {
    IO.withTemporaryDirectory { tempDir =>
      val si = scalaInstanceFromFile(scalaVersion)
      val sc = scalaCompiler(si, compilerBridge(si, tempDir, log))
      val cs = compiler.compilers(si, ClasspathOptions.boot, None, sc)
      val analysisMap = f1[File, Maybe[CompileAnalysis]](f => Maybe.nothing[CompileAnalysis])
      val dc = f1[File, DefinesClass](f => new DefinesClass {
        override def apply(className: String): Boolean = false
      })
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val reporter = new LoggerReporter(maxErrors, log, identity)
      val setup = compiler.setup(analysisMap, dc, false, tempDir / "inc_compile", CompilerCache.fresh,
        incOptions, reporter)
      val prev = compiler.emptyPreviousResult
      val classesDir = tempDir / "classes"
      val in = compiler.inputs(si.allJars, Array(knownSampleGoodFile), classesDir, Array(), Array(), 100, Array(), CompileOrder.Mixed,
        cs, setup, prev)
      compiler.compile(in, log)
      val expectedOuts = List(classesDir / "test" / "pkg" / "Good$.class")
      expectedOuts foreach { f => assert(f.exists) }
    }
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(bridgeJar), ClasspathOptions.boot)

  def compilerBridge(scalaInstance: ScalaInstance, cacheDir: File, log: Logger): File = {

    val dir = cacheDir / bridgeId(scalaInstance.actualVersion)
    val bridgeJar = dir / (CompilerBridgeId + ".jar")

    if (!bridgeJar.exists) {
      dir.mkdirs()

      val sourceModule = {
        val dummyModule = ModuleID(xsbti.ArtifactInfo.SbtOrganization + "-tmp", "tmp-module", ComponentCompiler.incrementalVersion, Some("compile"))
        val source = ModuleID(xsbti.ArtifactInfo.SbtOrganization, CompilerBridgeId, ComponentCompiler.incrementalVersion, Some("compile")).sources()
        module(dummyModule, Seq(source), None)
      }

      val compilerBridgeArtifacts =
        for {
          conf <- ivyUpdate(sourceModule).configurations
          m <- conf.modules
          (_, f) <- m.artifacts
        } yield f

      val compilerBridgeSource = compilerBridgeArtifacts find (_.getName endsWith "-sources.jar") getOrElse ???
      val compilerInterfaceJar = compilerBridgeArtifacts find (_.getName startsWith "compiler-interface") getOrElse ???
      val utilInterfaceJar = compilerBridgeArtifacts find (_.getName startsWith "util-interface") getOrElse ???

      compileBridgeJar(CompilerBridgeId, compilerBridgeSource, bridgeJar, compilerInterfaceJar :: utilInterfaceJar :: Nil, scalaInstance, log)
    }
    bridgeJar
  }
  def bridgeId(scalaVersion: String) = CompilerBridgeId + "-" + scalaVersion + "-" + JavaClassVersion
  def compileBridgeJar(label: String, sourceJar: File, targetJar: File, xsbtiJars: Iterable[File], instance: ScalaInstance, log: Logger): Unit = {
    val raw = new RawCompiler(instance, ClasspathOptions.auto, log)
    AnalyzingCompiler.compileSources(sourceJar :: Nil, targetJar, xsbtiJars, label, raw, log)
  }

  def scalaInstanceFromFile(v: String): ScalaInstance =
    scalaInstance(
      ivyCache / "org.scala-lang" / "scala-compiler" / "jars" / s"scala-compiler-$v.jar",
      ivyCache / "org.scala-lang" / "scala-library" / "jars" / s"scala-library-$v.jar",
      Seq(ivyCache / "org.scala-lang" / "scala-reflect" / "jars" / s"scala-reflect-$v.jar")
    )
  def scalaInstance(scalaCompiler: File, scalaLibrary: File, scalaExtra: Seq[File]): ScalaInstance =
    {
      val loader = scalaLoader(scalaLibrary +: scalaCompiler +: scalaExtra)
      val version = scalaVersion(loader)
      val allJars = (scalaLibrary +: scalaCompiler +: scalaExtra).toArray
      new ScalaInstance(version.getOrElse("unknown"), loader, scalaLibrary, scalaCompiler, allJars, version)
    }
  def scalaLoader(jars: Seq[File]) = new URLClassLoader(toURLs(jars), ClasspathUtilities.rootLoader)
  def scalaVersion(scalaLoader: ClassLoader): Option[String] = {
    propertyFromResource("compiler.properties", "version.number", scalaLoader)
  }
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
  def propertiesFromResource(resource: String, classLoader: ClassLoader): java.util.Properties = {
    val props = new java.util.Properties
    val stream = classLoader.getResourceAsStream(resource)
    try { props.load(stream) }
    catch { case e: Exception => }
    finally { if (stream ne null) stream.close }
    props
  }
  def f1[A, B](f: A => B): F1[A, B] =
    new F1[A, B] {
      def apply(a: A): B = f(a)
    }
}
