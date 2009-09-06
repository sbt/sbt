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
			FileUtilities.withTemporaryDirectory { temp =>
				val launch = new xsbt.boot.Launch(temp)
				val sbtVersion = xsbti.Versions.Sbt
				val manager = new ComponentManager(launch.getSbtHome(sbtVersion, scalaVersion), log)
				prepare(manager, ComponentCompiler.compilerInterfaceSrcID, "CompilerInterface.scala")
				prepare(manager, ComponentCompiler.xsbtiID, classOf[xsbti.AnalysisCallback])
				val result = f(AnalyzingCompiler(scalaVersion,  launch, manager), log)
				launch.clearScalaLoaderCache
				System.gc()
				System.gc()
				System.gc()
				result
			}
		}
	}
	private def prepare(manager: ComponentManager, id: String, resource: Class[_]): Unit =
	{
		val src = FileUtilities.classLocationFile(resource)
		prepare(manager, id, src)
	}
	private def prepare(manager: ComponentManager, id: String, resource: String): Unit =
	{
		val src = getClass.getClassLoader.getResource(resource)
		if(src eq null)
			error("Resource not found: " + resource)
		prepare(manager, id, FileUtilities.asFile(src))
	}
	import Paths._
	private def prepare(manager: ComponentManager, id: String, file: File): Unit =
		FileUtilities.copy(file x FileMapper.flat(manager.location(id)))
}