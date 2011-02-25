package sbt
package compile

	import java.io.File
	import IO.withTemporaryDirectory
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
			WithCompiler( "2.8.0" )(testCompileAnalysis)
			WithCompiler( "2.8.1" )(testCompileAnalysis)
		}
	}

	"Raw compiler" should {
		"Properly handle classpaths" in {
			testClasspath("2.7.2")
			testClasspath("2.7.7")
			testClasspath("2.8.0")
			testClasspath("2.8.1")
		}
	}
	
	private def testCompileAnalysis(compiler: AnalyzingCompiler, log: Logger)
	{
		WithFiles( new File("Test.scala") -> "object Test" ) { sources =>
			withTemporaryDirectory { temp =>
				val callback = new xsbti.TestCallback
				compiler(sources, Nil, temp, Nil, callback, 10, log)
				(callback.beganSources) must haveTheSameElementsAs(sources)
			}
		}
	}
	
	val UsingCompiler = "object Test { classOf[scala.tools.nsc.Global] }"
	
	private def shouldFail(act: => Unit) =
	{
		val success = try { act; true } catch { case t if expectedException(t) => false }
		if(success) error("Expected exception not thrown")
	}
	private def expectedException(t: Throwable) =
		t match
		{
			case e: Exception => true
			case t if isMissingRequirementError(t) => true
			case _ => false
		}
	private def isMissingRequirementError(t: Throwable) = t.getClass.getName == "scala.tools.nsc.MissingRequirementError"
	private def testClasspath(scalaVersion: String) =
		WithCompiler.launcher { (launch, log) =>
			def compiler(autoBoot: Boolean, compilerOnClasspath: Boolean): RawCompiler =
				new RawCompiler(ScalaInstance(scalaVersion, launch), new ClasspathOptions(autoBoot, compilerOnClasspath, true), log)

			val callback = new xsbti.TestCallback
				
			val standard = compiler(true, true)
			val noCompiler = compiler(true, false)
			val fullExplicit = compiler(false, false)
			
			val fullBoot = "-bootclasspath" :: fullExplicit.compilerArguments.createBootClasspath :: Nil
			val withCompiler = noCompiler.scalaInstance.compilerJar :: Nil
			
			WithFiles( new File("Test.scala") -> "object Test", new File("Test2.scala") -> UsingCompiler ) { case Seq(plain, useCompiler) =>
				val plainSrcs = Seq[File](plain)
				val compSrcs = Seq[File](useCompiler)
				withTemporaryDirectory { out =>
					standard(plainSrcs, Nil,  out, Nil) //success
					standard(compSrcs, Nil,  out, Nil) //success
					
					noCompiler(plainSrcs, Nil,  out, Nil) //success
					shouldFail( noCompiler(compSrcs, Nil,  out, Nil) )
					noCompiler(compSrcs, withCompiler, out, Nil) //success
					
					shouldFail( fullExplicit(plainSrcs, Nil, out, Nil) )// failure
					shouldFail( fullExplicit(compSrcs, Nil, out, Nil) )// failure
					fullExplicit(plainSrcs, Nil, out, fullBoot) // success
					fullExplicit(compSrcs, withCompiler, out, fullBoot) // success

					// specs now marks something as skipped if there are no matchers
					// this test throws exceptions on failure, so use a dummy check here
					true must be equalTo(true)
				}
			}
		}
}
