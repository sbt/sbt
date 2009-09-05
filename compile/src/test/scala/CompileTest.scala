package xsbt

import java.io.File
import FileUtilities.withTemporaryDirectory
import org.specs._

// compile w/ analysis a bit hard to test properly right now:
//  requires compile project to depend on +publish-local, which is not possible in sbt (addressed in xsbt, but that doesn't help here!)
object CompileTest extends Specification
{
	"Analysis compiler" should {
		"compile basic sources" in {
			WithCompiler( "2.7.2" )(testCompileAnalysis)
		}
	}
	private def testCompileAnalysis(compiler: AnalyzeCompiler, log: CompileLogger)
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
	def apply[T](scalaVersion: String)(f: (AnalyzeCompiler, CompileLogger) => T): T =
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
				f(AnalyzeCompiler(scalaVersion,  launch, manager), log)
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