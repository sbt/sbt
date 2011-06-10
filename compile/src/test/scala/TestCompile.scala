package sbt
package compiler

	import java.io.File
	import java.net.URLClassLoader
	import xsbti.TestCallback
	import IO.withTemporaryDirectory

object TestCompile
{
	def allVersions = List("2.8.1", "2.9.0-1")
	/** Tests running the compiler interface with the analyzer plugin with a test callback.  The test callback saves all information
	* that the plugin sends it for post-compile analysis by the provided function.*/
	def apply[T](scalaVersion: String, sources: Seq[File], outputDirectory: File, options: Seq[String])
		(f: (TestCallback, ScalaInstance, Logger) => T): T =
	{
		val testCallback = new TestCallback
		WithCompiler(scalaVersion) { (compiler, log) =>
			compiler(sources, Nil, outputDirectory, options, testCallback, 5, log)
			val result = f(testCallback, compiler.scalaInstance, log)
			for( (file, src) <- testCallback.apis )
				xsbt.api.APIUtil.verifyTypeParameters(src)
			result
		}
	}
	/** Tests running the compiler interface with the analyzer plugin.  The provided function is given a ClassLoader that can
	* load the compiled classes..*/
	def apply[T](scalaVersion: String, sources: Seq[File])(f: ClassLoader => T): T =
		CallbackTest.full(scalaVersion, sources){ case (_, outputDir, _, _) => f(new URLClassLoader(Array(outputDir.toURI.toURL))) }
}
object CallbackTest
{
	def simple[T](scalaVersion: String, sources: Seq[File])(f: TestCallback => T): T =
		full(scalaVersion, sources){ case (callback, _, _, _) => f(callback) }
	def full[T](scalaVersion: String, sources: Seq[File])(f: (TestCallback, File, ScalaInstance, Logger) => T): T =
		withTemporaryDirectory { outputDir =>
			TestCompile(scalaVersion, sources, outputDir, Nil) { case (callback, instance, log) => f(callback, outputDir, instance, log) }
		}
}