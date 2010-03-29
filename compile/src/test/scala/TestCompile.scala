package xsbt

import java.io.File
import java.net.URLClassLoader
import xsbti.TestCallback
import FileUtilities.withTemporaryDirectory

object TestCompile
{
	// skip 2.7.3 and 2.7.4 for speed
	def allVersions = List("2.7.2", "2.7.5", "2.7.7", "2.8.0.Beta1")//List("2.7.2", "2.7.3", "2.7.4", "2.7.5", "2.8.0-SNAPSHOT")
	/** Tests running the compiler interface with the analyzer plugin with a test callback.  The test callback saves all information
	* that the plugin sends it for post-compile analysis by the provided function.*/
	def apply[T](scalaVersion: String, sources: Set[File], outputDirectory: File, options: Seq[String], superclassNames: Seq[String], annotationNames: Seq[String])
		(f: (TestCallback, ScalaInstance, CompileLogger) => T): T =
	{
		val testCallback = new TestCallback(superclassNames.toArray, annotationNames.toArray)
		WithCompiler(scalaVersion) { (compiler, log) =>
			compiler(sources, Set.empty, outputDirectory, options, testCallback, 5, log)
			f(testCallback, compiler.scalaInstance, log)
		}
	}
	/** Tests running the compiler interface with the analyzer plugin.  The provided function is given a ClassLoader that can
	* load the compiled classes..*/
	def apply[T](scalaVersion: String, sources: Seq[File])(f: ClassLoader => T): T =
		CallbackTest.apply(scalaVersion, sources, Nil, Nil){ case (_, outputDir, _, _) => f(new URLClassLoader(Array(outputDir.toURI.toURL))) }
}
object CallbackTest
{
	def apply[T](scalaVersion: String, sources: Iterable[File])(f: TestCallback => T): T =
		apply(scalaVersion, sources.toSeq, Nil, Nil){ case (callback, _, _, _) => f(callback) }
	def apply[T](scalaVersion: String, sources: Seq[File], superclassNames: Seq[String], annotationNames: Seq[String])(f: (TestCallback, File, ScalaInstance, CompileLogger) => T): T =
		withTemporaryDirectory { outputDir =>
			TestCompile(scalaVersion, Set() ++ sources, outputDir, Nil, superclassNames, annotationNames) { case (callback, instance, log) => f(callback, outputDir, instance, log) }
		}
}