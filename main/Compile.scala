/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import xsbti.{Logger => _,_}
	import compile._
	import inc._
	import java.io.File

object Compile
{
	val DefaultMaxErrors = 100

	def allProblems(inc: Incomplete): Seq[Problem] =
		allProblems(inc :: Nil)
	def allProblems(incs: Seq[Incomplete]): Seq[Problem] =
		problems(Incomplete.allExceptions(incs).toSeq)
	def problems(es: Seq[Throwable]): Seq[Problem]  =
		es flatMap {
			case cf: xsbti.CompileFailed => cf.problems
			case _ => Nil
		}

	final class Inputs(val compilers: Compilers, val config: Options, val incSetup: IncSetup, val log: Logger)
	final class Options(val classpath: Seq[File], val sources: Seq[File], val classesDirectory: File, val options: Seq[String], val javacOptions: Seq[String], val maxErrors: Int)
	final class IncSetup(val javaSrcBases: Seq[File], val analysisMap: Map[File, Analysis], val cacheDirectory: File)
	final class Compilers(val scalac: AnalyzingCompiler, val javac: JavaCompiler)

	def inputs(classpath: Seq[File], sources: Seq[File], outputDirectory: File, options: Seq[String], javacOptions: Seq[String], javaSrcBases: Seq[File], maxErrors: Int)(implicit compilers: Compilers, log: Logger): Inputs =
	{
			import Path._
		val classesDirectory = outputDirectory / "classes"
		val cacheDirectory = outputDirectory / "cache"
		val augClasspath = classesDirectory.asFile +: classpath
		inputs(augClasspath, sources, classesDirectory, options, javacOptions, javaSrcBases, Map.empty, cacheDirectory, maxErrors)
	}
	def inputs(classpath: Seq[File], sources: Seq[File], classesDirectory: File, options: Seq[String], javacOptions: Seq[String], javaSrcBases: Seq[File], analysisMap: Map[File, Analysis], cacheDirectory: File, maxErrors: Int)(implicit compilers: Compilers, log: Logger): Inputs =
		new Inputs(
			compilers,
			new Options(classpath, sources, classesDirectory, options, javacOptions, maxErrors),
			new IncSetup(javaSrcBases, analysisMap, cacheDirectory),
			log
		)

	def compilers(implicit app: AppConfiguration, log: Logger): Compilers =
	{
		val scalaProvider = app.provider.scalaProvider
		compilers(ScalaInstance(scalaProvider.version, scalaProvider.launcher))
	}

	def compilers(instance: ScalaInstance)(implicit app: AppConfiguration, log: Logger): Compilers =
		compilers(instance, ClasspathOptions.auto)

	def compilers(instance: ScalaInstance, javac: (Seq[String], Logger) => Int)(implicit app: AppConfiguration, log: Logger): Compilers =
		compilers(instance, ClasspathOptions.auto, javac)

	def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions)(implicit app: AppConfiguration, log: Logger): Compilers =
	{
		val javac = JavaCompiler.directOrFork(cpOptions, instance)( (args: Seq[String], log: Logger) => Process("javac", args) ! log )
		compilers(instance, cpOptions, javac)
	}
	def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javac: (Seq[String], Logger) => Int)(implicit app: AppConfiguration, log: Logger): Compilers =
	{
		val javaCompiler = JavaCompiler.fork(cpOptions, instance)(javac)
		compilers(instance, cpOptions, javaCompiler)
	}
	def compilers(instance: ScalaInstance, cpOptions: ClasspathOptions, javac: JavaCompiler)(implicit app: AppConfiguration, log: Logger): Compilers =
	{
		val scalac = scalaCompiler(instance, cpOptions)
		new Compilers(scalac, javac)
	}
	def scalaCompiler(instance: ScalaInstance, cpOptions: ClasspathOptions)(implicit app: AppConfiguration, log: Logger): AnalyzingCompiler =
	{
		val launcher = app.provider.scalaProvider.launcher
		val componentManager = new ComponentManager(launcher.globalLock, app.provider.components, log)
		new AnalyzingCompiler(instance, componentManager, cpOptions, log)
	}

	def apply(in: Inputs): Analysis =
	{
			import in.compilers._
			import in.config._
			import in.incSetup._
		
		val agg = new build.AggressiveCompile(cacheDirectory)
		agg(scalac, javac, sources, classpath, classesDirectory, javaSrcBases, options, javacOptions, analysisMap, maxErrors)(in.log)
	}
}