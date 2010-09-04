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
				source(classpath, sourcepath, output, module, auto, name, configuration, allowMultiple)
			case ProjectLoad(base, auto, name) =>
				project(base, auto, name, configuration, allowMultiple)
		}

	def project(base: File, auto: Auto.Value, name: String, configuration: xsbti.AppConfiguration, allowMultiple: Boolean): Seq[Any] =
	{
		val buildDir = base / "project" / "build"
		val sources = (buildDir * "*.scala") +++ (buildDir / "src" / "main" / "scala" ** "*.scala")
		source(Nil, sources.get.toSeq, Some(buildDir / "target" asFile), None, auto, name, configuration, allowMultiple)
	}
		
	def binary(classpath: Seq[File], module: Boolean, name: String, parent: ClassLoader, allowMultiple: Boolean): Seq[Any] =
	{
		if(name.isEmpty)
			error("Class name required to load binary project.")
		else
		{
			val names = if(allowMultiple) name.split(",").toSeq else Seq(name)
			binaries(classpath, names.map(n => ToLoad(n,module)), parent)
		}
	}
	
	def compile(command: CompileCommand, configuration: xsbti.AppConfiguration): Analysis =
	{
		import command._
		compile(classpath, sources, output, options, configuration)
	}
	def compile(classpath: Seq[File], sources: Seq[File], output: Option[File], options: Seq[String], configuration: xsbti.AppConfiguration): Analysis =
		compile(new Compile(classpath, sources, output, options, configuration))

	def compile(conf : Compile): Analysis =
	{
		import conf._
		// TODO: accept Logger as an argument
		val log = ConsoleLogger()

		val componentManager = new ComponentManager(launcher.globalLock, configuration.provider.components, log)
		val compiler = new AnalyzingCompiler(instance, componentManager, log)
		val javac = JavaCompiler.directOrFork(compiler.cp, compiler.scalaInstance)( (args: Seq[String], log: Logger) => Process("javac", args) ! log )

		val agg = new AggressiveCompile(cacheDirectory)
		agg(compiler, javac, sources, compileClasspath, outputDirectory, Nil, options)(log)
	}
	def source(classpath: Seq[File], sources: Seq[File], output: Option[File], module: Option[Boolean], auto: Auto.Value, name: String, configuration: xsbti.AppConfiguration, allowMultiple: Boolean = false): Seq[Any] =
	{
		val conf = new Compile(classpath, sources, output, Nil, configuration)
		val analysis = compile(conf)
		val discovered = discover(analysis, module, auto, name)
		binaries(conf.projectClasspath, check(discovered, allowMultiple), loader(configuration))
	}
	def discover(analysis: inc.Analysis, module: Option[Boolean], auto: Auto.Value, name: String): Seq[ToLoad] =
	{
		import Auto.{Annotation, Explicit, Subclass}
		auto match {
			case Explicit => if(name.isEmpty) error("No name specified to load explicitly.") else Seq(new ToLoad(name))
			case Subclass => discover(analysis, module, new inc.Discovery(Set(name), Set.empty))
			case Annotation => discover(analysis, module, new inc.Discovery(Set.empty, Set(name)))
		}
	}
	def discover(analysis: inc.Analysis, command: DiscoverCommand): Seq[ToLoad] =
		discover(analysis, command.module, command.discovery)
		
	def discover(analysis: inc.Analysis, module: Option[Boolean], discovery: inc.Discovery): Seq[ToLoad] =
	{
		for(src <- analysis.apis.internal.values.toSeq;
			(df, found) <- discovery(src.definitions) if !found.isEmpty && moduleMatches(found.isModule, module))
		yield
			new ToLoad(df.name, found.isModule)
	}
	def moduleMatches(isModule: Boolean, expected: Option[Boolean]): Boolean =
		expected.isEmpty || (Some(isModule) == expected)

	def binaries(classpath: Seq[File], toLoad: Seq[ToLoad], parent: ClassLoader): Seq[Any] =
		loadBinaries(toLoad, toLoader(classpath, parent))

	def loadBinaries(toLoad: Seq[ToLoad], loader: ClassLoader): Seq[Any] =
		for(ToLoad(name, module) <- toLoad if !name.isEmpty) yield
			loadBinary(name, module, loader)

	def loadBinary(name: String, module: Boolean, loader: ClassLoader): Any =
		if(module)
			getObject(name, loader)
		else
		{
			val clazz = Class.forName(name, true, loader)
			clazz.newInstance
		}

	def check[T](discovered: Seq[T], allowMultiple: Boolean): Seq[T] =
		discovered match
		{
			case Seq() => error("No project found")
			case Seq(x) => discovered
			case _ => if(allowMultiple) discovered else error("Multiple projects found: " + discovered.mkString(", "))
		}
	
	def error(msg: String) = throw new BuildException(msg)
}