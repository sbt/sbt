/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt
package compiler

	import scala.util
	import java.io.File
	import CompilerArguments.{abs, absString, BootClasspathOption}

/** Forms the list of options that is passed to the compiler from the required inputs and other options.
* The directory containing scala-library.jar and scala-compiler.jar (scalaLibDirectory) is required in
* order to add these jars to the boot classpath. The 'scala.home' property must be unset because Scala
* puts jars in that directory on the bootclasspath.  Because we use multiple Scala versions,
* this would lead to compiling against the wrong library jar.*/
final class CompilerArguments(scalaInstance: ScalaInstance, cp: ClasspathOptions)
{
	def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String]): Seq[String] =
	{
		checkScalaHomeUnset()
		val cpWithCompiler = finishClasspath(classpath)
		// Scala compiler's treatment of empty classpath is troublesome (as of 2.9.1).
		// We append a random dummy element as workaround.
		val dummy = "dummy_" + Integer.toHexString(util.Random.nextInt)
		val classpathOption = Seq("-classpath", if(cpWithCompiler.isEmpty) dummy else absString(cpWithCompiler))
		val outputOption = Seq("-d", outputDirectory.getAbsolutePath)
		options ++ outputOption ++ bootClasspathOption ++ classpathOption ++ abs(sources)
	}
	def finishClasspath(classpath: Seq[File]): Seq[File] =
		filterLibrary(classpath) ++ include(cp.compiler, scalaInstance.compilerJar) ++ include(cp.extra, scalaInstance.extraJars : _*)
	private def include(flag: Boolean, jars: File*) = if(flag) jars else Nil
	protected def abs(files: Seq[File]) = files.map(_.getAbsolutePath).sortWith(_ < _)
	protected def checkScalaHomeUnset()
	{
		val scalaHome = System.getProperty("scala.home")
		assert((scalaHome eq null) || scalaHome.isEmpty, "'scala.home' should not be set (was " + scalaHome + ")")
	}
	/** Add the correct Scala library jar to the boot classpath.*/
	def createBootClasspath =
	{
		val originalBoot = System.getProperty("sun.boot.class.path", "")
		if(cp.bootLibrary)
		{
			val newBootPrefix = if(originalBoot.isEmpty) "" else originalBoot + File.pathSeparator
			newBootPrefix + scalaInstance.libraryJar.getAbsolutePath
		}
		else
			originalBoot
	}
	def filterLibrary(classpath: Seq[File]) =
		if(cp.filterLibrary) classpath.filterNot(_.getName contains ScalaArtifacts.LibraryID) else classpath
	def bootClasspathOption = if(cp.autoBoot) Seq(BootClasspathOption, createBootClasspath) else Nil
	def bootClasspath = if(cp.autoBoot) IO.parseClasspath(createBootClasspath) else Nil
}
object CompilerArguments
{
	val BootClasspathOption = "-bootclasspath"
	def abs(files: Seq[File]): Seq[String] = files.map(_.getAbsolutePath)
	def abs(files: Set[File]): Seq[String] = abs(files.toSeq)
	def absString(files: Seq[File]): String = abs(files).mkString(File.pathSeparator)
	def absString(files: Set[File]): String = absString(files.toSeq)
}
