package xsbt

import sbt.{ComponentManager, TestIvyLogger}

import java.io.File
import FileUtilities.withTemporaryDirectory
import org.specs._

object CompileTest extends Specification
{
	"Analysis compiler" should {
		"compile basic sources" in {
			WithCompiler( "2.7.2" )(testCompileAnalysis)
			WithCompiler( "2.7.3" )(testCompileAnalysis)
			WithCompiler( "2.7.4" )(testCompileAnalysis)
			WithCompiler( "2.7.5" )(testCompileAnalysis)
			WithCompiler( "2.7.7" )(testCompileAnalysis)
			WithCompiler( "2.8.0.Beta1" )(testCompileAnalysis)
			//WithCompiler( "2.8.0-SNAPSHOT" )(testCompileAnalysis)
		}
	}
	
	"Raw compiler" should {
		"Properly handle classpaths" in {
			testClasspath("2.7.2")
			testClasspath("2.7.7")
			testClasspath("2.8.0.Beta1")
		}
	}
	
	private def testCompileAnalysis(compiler: AnalyzingCompiler, log: CompileLogger)
	{
		WithFiles( new File("Test.scala") -> "object Test" ) { sources =>
			withTemporaryDirectory { temp =>
				val callback = new xsbti.TestCallback(Array(), Array())
				compiler(Set() ++ sources, Set.empty, temp, Nil, callback, 10, log)
				(callback.beganSources) must haveTheSameElementsAs(sources)
			}
		}
	}
	
	val UsingCompiler = "object Test { classOf[scala.tools.nsc.Global] }"
	
	private def shouldFail(act: => Unit) =
	{
		val success = try { act; true } catch { case e: Exception => false }
		if(success) error("Expected exception not thrown")
	}
	private def isMissingRequirementError(t: Throwable) = t.getClass.getName == "scala.tools.nsc.MissingRequirementError"
	private def testClasspath(scalaVersion: String) =
		WithCompiler.launcher { (launch, log) =>
			def compiler(autoBoot: Boolean, compilerOnClasspath: Boolean): RawCompiler =
				new RawCompiler(ScalaInstance(scalaVersion, launch), autoBoot, compilerOnClasspath, log)

			val callback = new xsbti.TestCallback(Array(), Array())
				
			val standard = compiler(true, true)
			val noCompiler = compiler(true, false)
			val fullExplicit = compiler(false, false)
			
			val fullBoot = "-bootclasspath" :: fullExplicit.compilerArguments.createBootClasspath :: Nil
			val withCompiler = Set() + noCompiler.scalaInstance.compilerJar
			
			WithFiles( new File("Test.scala") -> "object Test", new File("Test2.scala") -> UsingCompiler ) { case Array(plain, useCompiler) =>
				val plainSrcs = Set[File](plain)
				val compSrcs = Set[File](useCompiler)
				FileUtilities.withTemporaryDirectory { out =>
					standard(plainSrcs, Set.empty,  out, Nil) //success
					standard(compSrcs, Set.empty,  out, Nil) //success
					
					noCompiler(plainSrcs, Set.empty,  out, Nil) //success
					shouldFail( noCompiler(compSrcs, Set.empty,  out, Nil) )
					noCompiler(compSrcs, withCompiler,  out, Nil) //success
					
					shouldFail( fullExplicit(plainSrcs, Set.empty, out, Nil) )// failure
					shouldFail( fullExplicit(compSrcs, Set.empty, out, Nil) )// failure
					fullExplicit(plainSrcs, Set.empty, out, fullBoot) // success
					fullExplicit(compSrcs, withCompiler, out, fullBoot) // success
				}
			}
		}
}
object WithCompiler
{
	def apply[T](scalaVersion: String)(f: (AnalyzingCompiler, CompileLogger) => T): T =
	{
		launcher { (launch, log) =>
			FileUtilities.withTemporaryDirectory { componentDirectory =>
				val manager = new ComponentManager(xsbt.boot.Locks, new boot.ComponentProvider(componentDirectory), log)
				val compiler = new AnalyzingCompiler(ScalaInstance(scalaVersion, launch), manager, log)
				compiler.newComponentCompiler(log).clearCache(ComponentCompiler.compilerInterfaceID)
				define(manager, ComponentCompiler.compilerInterfaceSrcID, getResource("CompilerInterface.scala"), getClassResource(classOf[jline.Completor]))
				define(manager, ComponentCompiler.xsbtiID, getClassResource(classOf[xsbti.AnalysisCallback]))
				f(compiler, log)
			}
		}
	}
	def launcher[T](f: (xsbti.Launcher, TestIvyLogger with CompileLogger) => T): T =
	{
		val log = new TestIvyLogger with CompileLogger
		log.setLevel(Level.Debug)
		log.bufferQuietly {
			boot.LaunchTest.withLauncher { launch => f(launch, log) }
		}
	}
	def getClassResource(resource: Class[_]): File = FileUtilities.classLocationFile(resource)
	def getResource(resource: String): File = 
	{
		val src = getClass.getClassLoader.getResource(resource)
		if(src ne null) FileUtilities.asFile(src) else error("Resource not found: " + resource)
	}
	def define(manager: ComponentManager, id: String, files: File*)
	{
		manager.clearCache(id)
		manager.define(id, files)
	}
}