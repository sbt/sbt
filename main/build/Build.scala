/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package build

import java.io.File
import classpath.ClasspathUtilities.toLoader
import ModuleUtilities.getObject
import compile.{AnalyzingCompiler, JavaCompiler}
import inc.Analysis
import Path._
import GlobFilter._

final class BuildException(msg: String) extends RuntimeException(msg)

object Build
{
	def loader(configuration: xsbti.AppConfiguration): ClassLoader =
		configuration.provider.mainClass.getClassLoader
	
	def apply(command: LoadCommand, configuration: xsbti.AppConfiguration, allowMultiple: Boolean): Seq[Any] =
		command match
		{
			case BinaryLoad(classpath, module, name) =>
				binary(classpath, module, name, loader(configuration), allowMultiple)
			case SourceLoad(classpath, sourcepath, output, module, auto, name) =>
				source(classpath, sourcepath, output, module, auto, name, configuration, allowMultiple)._1
			case ProjectLoad(base, auto, name) =>
				project(base, auto, name, configuration, allowMultiple)._1
		}

	def project(base: File, auto: Auto.Value, name: String, configuration: xsbti.AppConfiguration, allowMultiple: Boolean): Seq[Any] =
	{
		val buildDir = base / "project" / "build"
		val sources = buildDir * "*.scala" +++ buildDir / "src" / "main" / "scala" ** "*.scala"
		source(Nil, sources.get.toSeq, Some(buildDir / "target" asFile), false, auto, name, configuration, allowMultiple)
	}
		
	def binary(classpath: Seq[File], module: Boolean, name: String, parent: ClassLoader, allowMultiple: Boolean): Seq[Any] =
	{
		if(name.isEmpty)
			error("Class name required to load binary project.")
		else
		{
			val names = if(allowMultiple) name.split(",").toSeq else Seq(name)
			binaries(classpath, module, names, parent)
		}
	}
	
	def source(classpath: Seq[File], sources: Seq[File], output: Option[File], module: Boolean, auto: Auto.Value, name: String, configuration: xsbti.AppConfiguration, allowMultiple: Boolean = false): (Seq[Any], Analysis) =
	{
		// TODO: accept Logger as an argument
		val log = new ConsoleLogger with Logger with sbt.IvyLogger
		
		val scalaProvider = configuration.provider.scalaProvider
		val launcher = scalaProvider.launcher
		val instance = ScalaInstance(scalaProvider.version, launcher)
		
		val out = output.getOrElse(configuration.baseDirectory / "target" asFile)
		val target = out / ("scala_" + instance.actualVersion)
		val outputDirectory = target / "classes"
		val cacheDirectory = target / "cache"
		val projectClasspath = outputDirectory.asFile +: classpath
		val compileClasspath = projectClasspath ++ configuration.provider.mainClasspath.toSeq
		
		val componentManager = new ComponentManager(launcher.globalLock, configuration.provider.components, log)
		val compiler = new AnalyzingCompiler(instance, componentManager, log)
		val javac = JavaCompiler.directOrFork(compiler.cp, compiler.scalaInstance)( (args: Seq[String], log: Logger) => Process("javac", args) ! log )

		val agg = new AggressiveCompile(cacheDirectory)
		val analysis = agg(compiler, javac, sources, compileClasspath, outputDirectory, Nil, Nil)(log)
		
		val discovered = discover(analysis, module, auto, name)
		val loaded = binaries(projectClasspath, module, check(discovered, allowMultiple), loader(configuration))

		(loaded, analysis)
	}
	def discover(analysis: inc.Analysis, module: Boolean, auto: Auto.Value, name: String): Seq[String] =
	{
		import Auto.{Annotation, Explicit, Subclass}
		auto match {
			case Explicit => if(name.isEmpty) error("No name specified to load explicitly.") else Seq(name)
			case Subclass => discover(analysis, module, new inc.Discovery(Set(name), Set.empty))
			case Annotation => discover(analysis, module, new inc.Discovery(Set.empty, Set(name)))
		}
	}
	def discover(analysis: inc.Analysis, module: Boolean, discovery: inc.Discovery): Seq[String] =
	{
		for(src <- analysis.apis.internal.values.toSeq;
			(df, found) <- discovery(src.definitions) if !found.isEmpty && found.isModule == module)
		yield
			df.name
	}

	def binaries(classpath: Seq[File], module: Boolean, names: Seq[String], parent: ClassLoader): Seq[Any] =
		loadBinaries(names, module, toLoader(classpath, parent))

	def loadBinaries(names: Seq[String], module: Boolean, loader: ClassLoader): Seq[Any] =
		for(name <- names if !name.isEmpty) yield
			loadBinary(name, module, loader)

	def loadBinary(name: String, module: Boolean, loader: ClassLoader): Any =
		if(module)
			getObject(name, loader)
		else
		{
			val clazz = Class.forName(name, true, loader)
			clazz.newInstance
		}

	def check(discovered: Seq[String], allowMultiple: Boolean): Seq[String] =
		discovered match
		{
			case Seq() => error("No project found")
			case Seq(x) => discovered
			case _ => if(allowMultiple) discovered else error("Multiple projects found: " + discovered.mkString(", "))
		}
	
	def error(msg: String) = throw new BuildException(msg)
}