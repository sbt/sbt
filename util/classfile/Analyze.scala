/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt
package classfile

import scala.collection.mutable
import mutable.{ArrayBuffer, Buffer}
import java.io.File
import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Modifier.{STATIC, PUBLIC, ABSTRACT}

private[sbt] object Analyze
{
	def apply[T](outputDirectory: Path, sources: Seq[Path], roots: Seq[Path], log: Logger)(analysis: xsbti.AnalysisCallback, loader: ClassLoader)(compile: => Unit)
	{
		val sourceSet = Set(sources.toSeq : _*)
		val classesFinder = outputDirectory ** GlobFilter("*.class")
		val existingClasses = classesFinder.get

		def load(tpe: String, errMsg: => Option[String]): Option[Class[_]] =
			try { Some(Class.forName(tpe, false, loader)) }
			catch { case e => errMsg.foreach(msg => log.warn(msg + " : " +e.toString)); None }

		// runs after compilation
		def analyze()
		{
			val allClasses = Set(classesFinder.get.toSeq : _*)
			val newClasses = allClasses -- existingClasses
			
			val productToSource = new mutable.HashMap[Path, Path]
			val sourceToClassFiles = new mutable.HashMap[Path, Buffer[ClassFile]]

			val superclasses = analysis.superclassNames flatMap { tpe => load(tpe, None) }
			val annotations = analysis.annotationNames.toSeq

			def annotated(fromClass: Seq[Annotation]) = if(fromClass.isEmpty) Nil else annotations.filter(fromClass.map(_.annotationType.getName).toSet)

			// parse class files and assign classes to sources.  This must be done before dependencies, since the information comes
			// as class->class dependencies that must be mapped back to source->class dependencies using the source+class assignment
			for(newClass <- newClasses;
				path <- Path.relativize(outputDirectory, newClass);
				classFile = Parser(newClass.asFile);
				sourceFile <- classFile.sourceFile orElse guessSourceName(newClass.asFile.getName);
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
					cls <- load(classFile.className, Some("Could not load '" + classFile.className + "' to check for superclasses.")) )
				{
					for(superclass <- superclasses)
						if(superclass.isAssignableFrom(cls))
							analysis.foundSubclass(source, classFile.className, superclass.getName, false)

					val annotations = new ArrayBuffer[String]
					annotations ++= annotated(cls.getAnnotations)
					for(method <- cls.getMethods)
					{
						annotations ++= annotated(method.getAnnotations)
						if(isMain(method))
							analysis.foundApplication(source, classFile.className)
					}
					annotations.foreach { ann => analysis.foundAnnotated(source, classFile.className, ann, false) }
				}
				def processDependency(tpe: String)
				{
					trapAndLog(log)
					{
						val loaded = load(tpe, Some("Problem processing dependencies of source " + source))
						for(clazz <- loaded; file <- ErrorHandling.convert(IO.classLocationFile(clazz)).right)
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
		
		compile
		analyze()
	}
	private def trapAndLog(log: Logger)(execute: => Unit)
	{
		try { execute }
		catch { case e => log.trace(e); log.error(e.toString) }
	}
	private def guessSourceName(name: String) = Some( takeToDollar(trimClassExt(name)) )
	private def takeToDollar(name: String) =
	{
		val dollar = name.indexOf('$')
		if(dollar < 0) name else name.substring(0, dollar)
	}
	private final val ClassExt = ".class"
	private def trimClassExt(name: String) = if(name.endsWith(ClassExt)) name.substring(0, name.length - ClassExt.length) else name
	private def resolveClassFile(file: File, className: String): File = (file /: (className.replace('.','/') + ClassExt).split("/"))(new File(_, _))
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
	private lazy val unit = classOf[Unit]
	private lazy val strArray = List(classOf[Array[String]])

	private def isMain(method: Method): Boolean =
		method.getName == "main" &&
		isMain(method.getModifiers) &&
		method.getReturnType == unit &&
		method.getParameterTypes.toList == strArray
	private def isMain(modifiers: Int): Boolean = (modifiers & mainModifiers) == mainModifiers && (modifiers & notMainModifiers) == 0
	
	private val mainModifiers = STATIC  | PUBLIC
	private val notMainModifiers = ABSTRACT
}