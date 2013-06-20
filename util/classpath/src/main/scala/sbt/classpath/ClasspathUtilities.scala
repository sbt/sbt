/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt
package classpath

import java.lang.ref.{Reference, SoftReference, WeakReference}
import java.io.File
import java.net.{URI, URL, URLClassLoader}
import java.util.Collections
import scala.collection.{mutable, JavaConversions, Set}
import mutable.{HashSet, ListBuffer}
import IO.{createTemporaryDirectory, write}

object ClasspathUtilities
{
	def toLoader(finder: PathFinder): ClassLoader = toLoader(finder, rootLoader)
	def toLoader(finder: PathFinder, parent: ClassLoader): ClassLoader = new URLClassLoader(finder.getURLs, parent)

	def toLoader(paths: Seq[File]): ClassLoader = toLoader(paths, rootLoader)
	def toLoader(paths: Seq[File], parent: ClassLoader): ClassLoader = new URLClassLoader(Path.toURLs(paths), parent)

	def toLoader(paths: Seq[File], parent: ClassLoader, resourceMap: Map[String,String]): ClassLoader =
		new URLClassLoader(Path.toURLs(paths), parent) with RawResources { override def resources = resourceMap }

	def toLoader(paths: Seq[File], parent: ClassLoader, resourceMap: Map[String,String], nativeTemp: File): ClassLoader =
		new URLClassLoader(Path.toURLs(paths), parent) with RawResources with NativeCopyLoader {
			override def resources = resourceMap
			override val config = new NativeCopyConfig(nativeTemp, paths, javaLibraryPaths)
		}

	def javaLibraryPaths: Seq[File] = IO.parseClasspath(System.getProperty("java.library.path"))

	lazy val rootLoader =
	{
		def parent(loader: ClassLoader): ClassLoader =
		{
			val p = loader.getParent
			if(p eq null) loader else parent(p)
		}
		val systemLoader = ClassLoader.getSystemClassLoader
		if (systemLoader ne null) parent(systemLoader)
		else parent(getClass.getClassLoader)
	}
	lazy val xsbtiLoader = classOf[xsbti.Launcher].getClassLoader
	
	final val AppClassPath = "app.class.path"
	final val BootClassPath = "boot.class.path"
	
	def createClasspathResources(classpath: Seq[File], instance: ScalaInstance): Map[String,String] =
		createClasspathResources(classpath, instance.jars)
		
	def createClasspathResources(appPaths: Seq[File], bootPaths: Seq[File]): Map[String, String] =
	{
		def make(name: String, paths: Seq[File]) = name -> Path.makeString(paths)
		Map( make(AppClassPath, appPaths), make(BootClassPath, bootPaths) )
	}

	private[sbt] def filterByClasspath(classpath: Seq[File], loader: ClassLoader): ClassLoader =
		new ClasspathFilter(loader, xsbtiLoader, classpath.toSet)

	def makeLoader(classpath: Seq[File], instance: ScalaInstance): ClassLoader =
		filterByClasspath(classpath, makeLoader(classpath, instance.loader, instance))

	def makeLoader(classpath: Seq[File], instance: ScalaInstance, nativeTemp: File): ClassLoader =
		filterByClasspath(classpath, makeLoader(classpath, instance.loader, instance, nativeTemp))

	def makeLoader(classpath: Seq[File], parent: ClassLoader, instance: ScalaInstance): ClassLoader =
		toLoader(classpath, parent, createClasspathResources(classpath, instance))

	def makeLoader(classpath: Seq[File], parent: ClassLoader, instance: ScalaInstance, nativeTemp: File): ClassLoader =
		toLoader(classpath, parent, createClasspathResources(classpath, instance), nativeTemp)

	private[sbt] def printSource(c: Class[_]) =
		println(c.getName + " loader=" +c.getClassLoader + " location=" + IO.classLocationFile(c))
	
	def isArchive(file: File): Boolean = isArchive(file, contentFallback = false)

	def isArchive(file: File, contentFallback: Boolean): Boolean =
		file.isFile && (isArchiveName(file.getName) || (contentFallback && hasZipContent(file)))

	def isArchiveName(fileName: String) = fileName.endsWith(".jar") || fileName.endsWith(".zip")

	def hasZipContent(file: File): Boolean = try {
		Using.fileInputStream(file) { in =>
			(in.read() == 0x50) &&
			(in.read() == 0x4b) &&
			(in.read() == 0x03) &&
			(in.read() == 0x04)
		}
	} catch { case e: Exception => false }
	
	/** Returns all entries in 'classpath' that correspond to a compiler plugin.*/
	private[sbt] def compilerPlugins(classpath: Seq[File]): Iterable[File] =
	{
		import collection.JavaConversions._
		val loader = new URLClassLoader(Path.toURLs(classpath))
		loader.getResources("scalac-plugin.xml").toList.flatMap(asFile(true))
	}
	/** Converts the given URL to a File.  If the URL is for an entry in a jar, the File for the jar is returned. */
	private[sbt] def asFile(url: URL): List[File] = asFile(false)(url)
	private[sbt] def asFile(jarOnly: Boolean)(url: URL): List[File] =
	{
		try
		{
			url.getProtocol match
			{
				case "file" if !jarOnly=> IO.toFile(url) :: Nil
				case "jar" =>
					val path = url.getPath
					val end = path.indexOf('!')
					new File(new URI(if(end == -1) path else path.substring(0, end))) :: Nil
				case _ => Nil
			}
		}
		catch { case e: Exception => Nil }
	}
}
