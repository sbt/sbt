/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import java.io.File
import xsbt.{AnalyzingCompiler, CompileFailed, CompilerArguments, ScalaInstance}

object CompileOrder extends Enumeration
{
	val Mixed, JavaThenScala, ScalaThenJava = Value
}

sealed abstract class CompilerCore
{
	final def apply(label: String, sources: Iterable[Path], classpath: Iterable[Path], outputDirectory: Path, scalaOptions: Seq[String], log: Logger): Option[String] =
		apply(label, sources, classpath, outputDirectory, scalaOptions, Nil, CompileOrder.Mixed, log)
	final def apply(label: String, sources: Iterable[Path], classpath: Iterable[Path], outputDirectory: Path, scalaOptions: Seq[String], javaOptions: Seq[String], order: CompileOrder.Value, log: Logger): Option[String] =
	{
			def filteredSources(extension: String) = sources.filter(_.name.endsWith(extension))
			def process(label: String, sources: Iterable[_], act: => Unit) =
				() => if(sources.isEmpty) log.debug("No " + label + " sources.") else act

		val javaSources = Path.getFiles(filteredSources(".java"))
		val scalaSources = Path.getFiles( if(order == CompileOrder.Mixed) sources else filteredSources(".scala") )
		val classpathSet = Path.getFiles(classpath)
		val scalaCompile = process("Scala", scalaSources, processScala(scalaSources, classpathSet, outputDirectory.asFile, scalaOptions, log) )
		val javaCompile = process("Java", javaSources, processJava(javaSources, classpathSet, outputDirectory.asFile, javaOptions, log))
		doCompile(label, sources, outputDirectory, order, log)(javaCompile, scalaCompile)
	}
	protected def doCompile(label: String, sources: Iterable[Path], outputDirectory: Path, order: CompileOrder.Value, log: Logger)(javaCompile: () => Unit, scalaCompile: () => Unit) =
	{
		log.info(actionStartMessage(label))
		if(sources.isEmpty)
		{
			log.info(actionNothingToDoMessage)
			None
		}
		else
		{
			FileUtilities.createDirectory(outputDirectory.asFile, log) orElse
				(try
				{
					val (first, second) = if(order == CompileOrder.JavaThenScala) (javaCompile, scalaCompile) else (scalaCompile, javaCompile)
					first()
					second()
					log.info(actionSuccessfulMessage)
					None
				}
				catch { case e: xsbti.CompileFailed => Some(e.toString) })
		}
	}
	def actionStartMessage(label: String): String
	def actionNothingToDoMessage: String
	def actionSuccessfulMessage: String
	protected def processScala(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger): Unit
	protected def processJava(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger): Unit
}

sealed abstract class CompilerBase extends CompilerCore
{
	def actionStartMessage(label: String) = "Compiling " + label + " sources..."
	val actionNothingToDoMessage = "Nothing to compile."
	val actionSuccessfulMessage = "Compilation successful."
}

// The following code is based on scala.tools.nsc.Main and scala.tools.nsc.ScalaDoc
// Copyright 2005-2008 LAMP/EPFL
// Original author: Martin Odersky

final class Compile(maximumErrors: Int, compiler: AnalyzingCompiler, analysisCallback: AnalysisCallback, baseDirectory: Path) extends CompilerBase with WithArgumentFile
{
	protected def processScala(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger)
	{
		val callbackInterface = new AnalysisInterface(analysisCallback, baseDirectory, outputDirectory)
		compiler(Set() ++ sources, Set() ++ classpath, outputDirectory, options, callbackInterface, maximumErrors, log)
	}
	protected def processJava(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger)
	{
		val augmentedClasspath = if(compiler.autoBootClasspath) classpath + compiler.scalaInstance.libraryJar else classpath
		val arguments = (new CompilerArguments(compiler.scalaInstance, false, compiler.compilerOnClasspath))(sources, augmentedClasspath, outputDirectory, options)
		log.debug("Calling 'javac' with arguments:\n\t" + arguments.mkString("\n\t"))
		def javac(argFile: File) = Process("javac", ("@" + normalizeSlash(argFile.getAbsolutePath)) :: Nil) ! log
		val code = withArgumentFile(arguments)(javac)
		if( code != 0 ) throw new CompileFailed(arguments.toArray, "javac returned nonzero exit code")
	}
}
trait WithArgumentFile extends NotNull
{
	def withArgumentFile[T](args: Seq[String])(f: File => T): T =
	{
		import xsbt.FileUtilities._
		withTemporaryDirectory { tmp =>
			val argFile = new File(tmp, "argfile")
			write(argFile, args.map(escapeSpaces).mkString(FileUtilities.Newline))
			f(argFile)
		}
	}
	// javac's argument file seems to allow naive space escaping with quotes.  escaping a quote with a backslash does not work
	def escapeSpaces(s: String): String = '\"' + normalizeSlash(s) + '\"'
	def normalizeSlash(s: String) = s.replace(File.separatorChar, '/')
}
final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler) extends CompilerCore
{
	protected def processScala(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger): Unit =
		compiler.doc(sources, classpath, outputDirectory, options, maximumErrors, log)
	protected def processJava(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], log: Logger) = ()

	def actionStartMessage(label: String) = "Generating API documentation for " + label + " sources..."
	val actionNothingToDoMessage = "No sources specified."
	val actionSuccessfulMessage = "API documentation generation successful."
	def actionUnsuccessfulMessage = "API documentation generation unsuccessful."
}
final class Console(compiler: AnalyzingCompiler) extends NotNull
{
	/** Starts an interactive scala interpreter session with the given classpath.*/
	def apply(classpath: Iterable[Path], log: Logger): Option[String] =
		apply(classpath, "", log)
	def apply(classpath: Iterable[Path], initialCommands: String, log: Logger): Option[String] =
	{
		def console0 = compiler.console(Path.getFiles(classpath), initialCommands, log)
		JLine.withJLine( Run.executeTrapExit(console0, log) )
	}
}

private final class AnalysisInterface(delegate: AnalysisCallback, basePath: Path, outputDirectory: File) extends xsbti.AnalysisCallback with NotNull
{
	val outputPath = Path.fromFile(outputDirectory)
	def superclassNames = delegate.superclassNames.toSeq.toArray[String]
	def annotationNames = delegate.annotationNames.toSeq.toArray[String]
	def superclassNotFound(superclassName: String) = delegate.superclassNotFound(superclassName)
	def beginSource(source: File) = delegate.beginSource(srcPath(source))

	def foundSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean) =
		delegate.foundSubclass(srcPath(source), subclassName, superclassName, isModule)
	def foundAnnotated(source: File, className: String, annotationName: String, isModule: Boolean) =
		delegate.foundAnnotated(srcPath(source), className, annotationName, isModule)
	def foundApplication(source: File, className: String) = delegate.foundApplication(srcPath(source), className)

	def sourceDependency(dependsOn: File, source: File) =
		delegate.sourceDependency(srcPath(dependsOn), srcPath(source))
	def jarDependency(jar: File, source: File) = delegate.jarDependency(jar, srcPath(source))
	def generatedClass(source: File, clazz: File) = delegate.generatedClass(srcPath(source), classPath(clazz))
	def endSource(source: File) = delegate.endSource(srcPath(source))

	def classDependency(clazz: File, source: File) =
	{
		val sourcePath = srcPath(source)
		Path.relativize(outputPath, clazz) match
		{
			case None =>  // dependency is a class file outside of the output directory
				delegate.classDependency(clazz, sourcePath)
			case Some(relativeToOutput) => // dependency is a product of a source not included in this compilation
				delegate.productDependency(relativeToOutput, sourcePath)
		}
	}
	def relativizeOrAbs(base: Path, file: File) = Path.relativize(base, file).getOrElse(Path.fromFile(file))
	def classPath(file: File) = relativizeOrAbs(outputPath, file)
	def srcPath(file: File) = relativizeOrAbs(basePath, file)
	def api(file: File, source: xsbti.api.Source) = delegate.api(srcPath(file), source)
}