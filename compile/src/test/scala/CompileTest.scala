package xsbt

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
					val manager = new ComponentManager(new boot.ComponentProvider(componentDirectory), log)
					prepare(manager, ComponentCompiler.compilerInterfaceSrcID, "CompilerInterface.scala")
					prepare(manager, ComponentCompiler.xsbtiID, classOf[xsbti.AnalysisCallback])
					f(new AnalyzingCompiler(ScalaInstance(scalaVersion, launch), manager), log)
				}
			}
		}
	}
	private def prepare(manager: ComponentManager, id: String, resource: Class[_]): Unit =
		manager.define(id, FileUtilities.classLocationFile(resource) :: Nil)
	private def prepare(manager: ComponentManager, id: String, resource: String): Unit =
	{
		val src = getClass.getClassLoader.getResource(resource)
		if(src eq null)
			error("Resource not found: " + resource)
		manager.define(id, FileUtilities.asFile(src) :: Nil)
	}
}