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
	
	lazy val rootLoader =
	{
		def parent(loader: ClassLoader): ClassLoader =
		{
			val p = loader.getParent
			if(p eq null) loader else parent(p)
		}
		parent(getClass.getClassLoader)
	}
	
	final val AppClassPath = "app.class.path"
	final val BootClassPath = "boot.class.path"
	
	def createClasspathResources(classpath: Seq[File], instance: ScalaInstance): Map[String,String] =
		createClasspathResources(classpath ++ instance.jars, instance.jars)
		
	def createClasspathResources(appPaths: Seq[File], bootPaths: Seq[File]): Map[String, String] =
	{
		def make(name: String, paths: Seq[File]) = name -> Path.makeString(paths)
		Map( make(AppClassPath, appPaths), make(BootClassPath, bootPaths) )
	}

	def makeLoader[T](classpath: Seq[File], instance: ScalaInstance): ClassLoader =
		makeLoader(classpath, instance.loader, instance)

	def makeLoader[T](classpath: Seq[File], parent: ClassLoader, instance: ScalaInstance): ClassLoader =
		toLoader(classpath, parent, createClasspathResources(classpath, instance))
	
	private[sbt] def printSource(c: Class[_]) =
		println(c.getName + " loader=" +c.getClassLoader + " location=" + IO.classLocationFile(c))
	
	def isArchive(path: Path): Boolean = isArchive(path.asFile)
	def isArchive(file: File): Boolean = isArchiveName(file.getName)
	def isArchiveName(fileName: String) = fileName.endsWith(".jar") || fileName.endsWith(".zip")
	// Partitions the given classpath into (jars, directories)
	private[sbt] def separate(paths: Iterable[File]): (Iterable[File], Iterable[File]) = paths.partition(isArchive)
	// Partitions the given classpath into (jars, directories)
	private[sbt] def separatePaths(paths: Iterable[Path]) = separate(paths.map(_.asFile.getCanonicalFile))
	private[sbt] def buildSearchPaths(classpath: Iterable[Path]): (collection.Set[File], collection.Set[File]) =
	{
		val (jars, dirs) = separatePaths(classpath)
		(linkedSet(jars ++ extraJars.toList), linkedSet(dirs ++ extraDirs.toList))
	}
	private[sbt] def onClasspath(classpathJars: collection.Set[File], classpathDirectories: collection.Set[File], file: File): Boolean =
	{
		val f = file.getCanonicalFile
		if(ClasspathUtilities.isArchive(f))
			classpathJars(f)
		else
			classpathDirectories.toList.find(Path.relativize(_, f).isDefined).isDefined
	}
	
	/** Returns all entries in 'classpath' that correspond to a compiler plugin.*/
	private[sbt] def compilerPlugins(classpath: Iterable[Path]): Iterable[File] =
	{
		import collection.JavaConversions._
		val loader = new URLClassLoader(Path.getURLs(classpath))
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
	
	private lazy val (extraJars, extraDirs) =
	{
		import scala.tools.nsc.GenericRunnerCommand
		val settings = (new GenericRunnerCommand(Nil, message => error(message))).settings
		val bootPaths = IO.pathSplit(settings.bootclasspath.value).map(p => new File(p)).toList
		val (bootJars, bootDirs) = separate(bootPaths)
		val extJars =
		{
			val buffer = new ListBuffer[File]
			def findJars(dir: File)
			{
				buffer ++= dir.listFiles(new SimpleFileFilter(isArchive))
				for(dir <- dir.listFiles(DirectoryFilter))
					findJars(dir)
			}
			for(path <- IO.pathSplit(settings.extdirs.value); val dir = new File(path) if dir.isDirectory)
				findJars(dir)
			buffer.readOnly.map(_.getCanonicalFile)
		}
		(linkedSet(extJars ++ bootJars), linkedSet(bootDirs))
	}
	private def linkedSet[T](s: Iterable[T]): Set[T] =
	{
		val set: mutable.Set[T] = JavaConversions.asScalaSet(new java.util.LinkedHashSet[T])
		set ++= s
		set
	}
}

private class IntermediateLoader(urls: Array[URL], parent: ClassLoader) extends LoaderBase(urls, parent)
{
	def doLoadClass(className: String): Class[_] =
	{
		// if this loader is asked to load an sbt class, it must be because the project we are building is sbt itself,
		 // so we want to load the version of classes on the project classpath, not the parent
		if(className.startsWith(Loaders.SbtPackage))
			findClass(className)
		else
			defaultLoadClass(className)
	}
}
/** Delegates class loading to `parent` for all classes included by `filter`.  An attempt to load classes excluded by `filter`
* results in a `ClassNotFoundException`.*/
class FilteredLoader(parent: ClassLoader, filter: ClassFilter) extends ClassLoader(parent)
{
	require(parent != null) // included because a null parent is legitimate in Java
	def this(parent: ClassLoader, excludePackages: Iterable[String]) = this(parent, new ExcludePackagesFilter(excludePackages))
	
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		if(filter.include(className))
			super.loadClass(className, resolve)
		else
			throw new ClassNotFoundException(className)
	}
}
private class SelectiveLoader(urls: Array[URL], parent: ClassLoader, filter: ClassFilter) extends URLClassLoader(urls, parent)
{
	require(parent != null) // included because a null parent is legitimate in Java
	def this(urls: Array[URL], parent: ClassLoader, includePackages: Iterable[String]) = this(urls, parent, new IncludePackagesFilter(includePackages))
	
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		if(filter.include(className))
			super.loadClass(className, resolve)
		else
		{
			val loaded = parent.loadClass(className)
			if(resolve)
				resolveClass(loaded)
			loaded
		}
	}
}
trait ClassFilter
{
	def include(className: String): Boolean
}
abstract class PackageFilter(packages: Iterable[String]) extends ClassFilter
{
	require(packages.forall(_.endsWith(".")))
	protected final def matches(className: String): Boolean = packages.exists(className.startsWith)
}
class ExcludePackagesFilter(exclude: Iterable[String]) extends PackageFilter(exclude)
{
	def include(className: String): Boolean = !matches(className)
}
class IncludePackagesFilter(include: Iterable[String]) extends PackageFilter(include)
{
	def include(className: String): Boolean = matches(className)
}

private[sbt] class LazyFrameworkLoader(runnerClassName: String, urls: Array[URL], parent: ClassLoader, grandparent: ClassLoader)
	extends LoaderBase(urls, parent)
{
	def doLoadClass(className: String): Class[_] =
	{
		if(Loaders.isNestedOrSelf(className, runnerClassName))
			findClass(className)
		else if(Loaders.isSbtClass(className)) // we circumvent the parent loader because we know that we want the
			grandparent.loadClass(className)              // version of sbt that is currently the builder (not the project being built)
		else
			parent.loadClass(className)
	}
}
private object Loaders
{
	val SbtPackage = "sbt."
	def isNestedOrSelf(className: String, checkAgainst: String) =
		className == checkAgainst || className.startsWith(checkAgainst + "$")
	def isSbtClass(className: String) = className.startsWith(Loaders.SbtPackage)
}