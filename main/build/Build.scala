/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package build

import java.io.File
import classpath.ClasspathUtilities.toLoader
import ModuleUtilities.getObject
import compile.{AnalyzingCompiler, JavaCompiler}
import Path._
import GlobFilter._

final class BuildException(msg: String) extends RuntimeException(msg)

object Build
{
	def loader(configuration: xsbti.AppConfiguration): ClassLoader =
		configuration.provider.mainClass.getClassLoader
	
	def apply(command: LoadCommand, configuration: xsbti.AppConfiguration): Any =
		command match
		{
			case BinaryLoad(classpath, module, name) =>
				binary(classpath, module, name, loader(configuration))
			case SourceLoad(classpath, sourcepath, output, module, auto, name) =>
				source(classpath, sourcepath, output, module, auto, name, configuration)
			case ProjectLoad(base, name) =>
				project(base, name, configuration)
		}

	def project(base: File, name: String, configuration: xsbti.AppConfiguration): Any =
	{
		val nonEmptyName = if(name.isEmpty) "Project" else name
		val buildDir = base / "project" / "build"
		val sources = buildDir * "*.scala" +++ buildDir / "src" / "main" / "scala" ** "*.scala"
		source(Nil, sources.get.toSeq, Some(buildDir / "target" asFile), false, Auto.Explicit, nonEmptyName, configuration)
	}
		
	def binary(classpath: Seq[File], module: Boolean, name: String, parent: ClassLoader): Any =
	{
		if(name.isEmpty)
			error("Class name required to load binary project.")
		else
		{
			val loader = toLoader(classpath, parent)
			if(module)
				getObject(name, loader)
			else
			{
				val clazz = Class.forName(name, true, loader)
				clazz.newInstance
			}
		}
	}
	
	def source(classpath: Seq[File], sources: Seq[File], output: Option[File], module: Boolean, auto: Auto.Value, name: String, configuration: xsbti.AppConfiguration): Any =
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
		load(discovered)(x => binary(projectClasspath, module, x, loader(configuration)) )
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
			(df, found) <- discovery(src.definitions) if found.isModule == module)
		yield
			df.name
	}
	def load(discovered: Seq[String])(doLoad: String => Any): Any =
		discovered match
		{
			case Seq() => error("No project found")
			case Seq(x) => doLoad(x)
			case xs => error("Multiple projects found: " + discovered.mkString(", "))
		}
	
	def error(msg: String) = throw new BuildException(msg)
}