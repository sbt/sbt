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
			TestIvyLogger { log =>
				LoadHelpers.withLaunch { launch =>
					val scalaVersion = "2.7.2"
					val sbtVersion = xsbti.Versions.Sbt
					val manager = new ComponentManager(launch.getSbtHome(sbtVersion, scalaVersion), log)
					prepare(manager, ComponentCompiler.compilerInterfaceSrcID, "CompilerInterface.scala")
					prepare(manager, ComponentCompiler.xsbtiID, classOf[xsbti.AnalysisCallback])
					testCompileAnalysis(new AnalyzeCompile(scalaVersion,  launch, manager), log)
				}
			}
		}
	}
	private def testCompileAnalysis(compiler: AnalyzeCompile, log: xsbti.Logger)
	{
		WithFiles( new File("Test.scala") -> "object Test" ) { sources =>
			withTemporaryDirectory { temp =>
				val arguments = "-d" :: temp.getAbsolutePath :: sources.map(_.getAbsolutePath).toList
				val callback = new xsbti.TestCallback(Array())
				compiler(arguments, callback, 10, log)
				(callback.beganSources) must haveTheSameElementsAs(sources)
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