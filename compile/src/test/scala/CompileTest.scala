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
			WithCompiler( "2.8.0-SNAPSHOT" )(testCompileAnalysis)
		}
	}
	private def testCompileAnalysis(compiler: AnalyzingCompiler, log: CompileLogger)
	{
		WithFiles( new File("Test.scala") -> "object Test" ) { sources =>
			withTemporaryDirectory { temp =>
				val callback = new xsbti.TestCallback(Array())
				compiler(Set() ++ sources, Set.empty, temp, Nil, callback, 10, log)
				(callback.beganSources) must haveTheSameElementsAs(sources)
			}
		}
	}
}
object WithCompiler
{
	def apply[T](scalaVersion: String)(f: (AnalyzingCompiler, CompileLogger) => T): T =
	{
		System.setProperty("scala.home", "") // need to make sure scala.home is unset
		val log = new TestIvyLogger with CompileLogger
		log.setLevel(Level.Debug)
		log.bufferQuietly {
			boot.LaunchTest.withLauncher { launch =>
				FileUtilities.withTemporaryDirectory { componentDirectory =>
					val manager = new ComponentManager(xsbt.boot.Locks, new boot.ComponentProvider(componentDirectory), log)
					val compiler = new AnalyzingCompiler(ScalaInstance(scalaVersion, launch), manager)
					compiler.newComponentCompiler(log).clearCache(ComponentCompiler.compilerInterfaceID)
					define(manager, ComponentCompiler.compilerInterfaceSrcID, getResource("CompilerInterface.scala"), getClassResource(classOf[jline.Completor]))
					define(manager, ComponentCompiler.xsbtiID, getClassResource(classOf[xsbti.AnalysisCallback]))
					f(compiler, log)
				}
			}
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