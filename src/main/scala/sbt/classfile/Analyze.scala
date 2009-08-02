/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.classfile
import sbt._

import scala.collection.mutable
import mutable.{ArrayBuffer, Buffer}
import java.io.File

private[sbt] object Analyze
{
	def apply[T](basePath: Path, outputDirectory: Path, sources: Iterable[Path], roots: Iterable[Path], log: Logger)
		(allProducts: => scala.collection.Set[Path], analysis: AnalysisCallback, loader: ClassLoader)
		(compile: => Option[String]): Option[String] =
	{
		val sourceSet = Set(sources.toSeq : _*)
		val classesFinder = outputDirectory ** GlobFilter("*.class")
		val existingClasses = classesFinder.get
		
		// runs after compilation
		def analyze()
		{
			val allClasses = Set(classesFinder.get.toSeq : _*)
			val newClasses = allClasses -- existingClasses -- allProducts
			
			val productToSource = new mutable.HashMap[Path, Path]
			val sourceToClassFiles = new mutable.HashMap[Path, Buffer[ClassFile]]
			
			// parse class files and assign classes to sources.  This must be done before dependencies, since the information comes
			// as class->class dependencies that must be mapped back to source->class dependencies using the source+class assignment
			for(newClass <- newClasses;
				path <- Path.relativize(outputDirectory, newClass);
				classFile = Parser(newClass.asFile, log);
				sourceFile <- classFile.sourceFile;
				source <- guessSourcePath(sourceSet, roots, classFile, log))
			{
				analysis.beginSource(source)
				analysis.generatedClass(source, path)
				productToSource(path) = source
				sourceToClassFiles.getOrElseUpdate(source, new ArrayBuffer[ClassFile]) += classFile
			}
			
			// get class to class dependencies and map back to source to class dependencies
			for( (source, classFiles) <- sourceToClassFiles )
			{
				for(classFile <- classFiles if isTopLevel(classFile);
					method <- classFile.methods; if method.isMain)
						analysis.foundApplication(source, classFile.className)
				def processDependency(tpe: String)
				{
					Control.trapAndLog(log)
					{
						val clazz = Class.forName(tpe, false, loader)
						for(file <- Control.convertException(FileUtilities.classLocationFile(clazz)).right)
						{
							if(file.isDirectory)
							{
								val resolved = resolveClassFile(file, tpe)
								assume(resolved.exists, "Resolved class file " + resolved + " from " + source + " did not exist")
								val resolvedPath = Path.fromFile(resolved)
								if(Path.fromFile(file) == outputDirectory)
								{
									productToSource.get(resolvedPath) match
									{
										case Some(dependsOn) => analysis.sourceDependency(dependsOn, source)
										case None => analysis.productDependency(resolvedPath, source)
									}
								}
								else
									analysis.classDependency(resolved, source)
							}
							else
								analysis.jarDependency(file, source)
						}
					}
				}
				
				classFiles.flatMap(_.types).foreach(processDependency)
				analysis.endSource(source)
			}
		}
		
		compile orElse Control.convertErrorMessage(log)(analyze()).left.toOption
	}
	private def resolveClassFile(file: File, className: String): File = (file /: (className.replace('.','/') + ".class").split("/"))(new File(_, _))
	private def guessSourcePath(sources: scala.collection.Set[Path], roots: Iterable[Path], classFile: ClassFile, log: Logger) =
	{
		val classNameParts = classFile.className.split("""\.""")
		val lastIndex = classNameParts.length - 1
		val pkg = classNameParts.take(lastIndex)
		val simpleClassName = classNameParts(lastIndex)
		val sourceFileName = classFile.sourceFile.getOrElse(simpleClassName.takeWhile(_ != '$').mkString("", "", ".java"))
		val relativeSourceFile = (pkg ++ (sourceFileName :: Nil)).mkString("/")
		val candidates = roots.map(root => Path.fromString(root, relativeSourceFile)).filter(sources.contains).toList
		candidates match
		{
			case Nil => log.warn("Could not determine source for class " + classFile.className)
			case head :: Nil => ()
			case _ =>log.warn("Multiple sources matched for class " + classFile.className + ": " + candidates.mkString(", "))
		}
		candidates
	}
	private def isTopLevel(classFile: ClassFile) = classFile.className.indexOf('$') < 0
}