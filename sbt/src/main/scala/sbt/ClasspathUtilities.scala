/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.io.File
import java.net.{URI, URL, URLClassLoader}
import java.util.Collections
import scala.collection.Set
import scala.collection.mutable.{HashSet, ListBuffer}
import xsbt.ScalaInstance
import xsbt.FileUtilities.{createTemporaryDirectory, withTemporaryDirectory, write}

object ClasspathUtilities
{
	def toClasspath(finder: PathFinder): Array[URL] = finder.getURLs
	def toClasspath(paths: Iterable[Path]): Array[URL] = Path.getURLs(paths)
	def toLoader(finder: PathFinder): ClassLoader = toLoader(finder.get)
	def toLoader(finder: PathFinder, parent: ClassLoader): ClassLoader = toLoader(finder.get, parent)
	def toLoader(paths: Iterable[Path]): ClassLoader = toLoader(paths, rootLoader)
	def toLoader(paths: Iterable[Path], parent: ClassLoader): ClassLoader = new URLClassLoader(toClasspath(paths), parent)
	
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
	
	def createClasspathResources(classpath: Iterable[Path], instance: ScalaInstance, baseDir: Path): Iterable[Path] =
		createClasspathResources(classpath ++ Path.fromFiles(instance.jars), Path.fromFiles(instance.jars), baseDir)
		
	def createClasspathResources(appPaths: Iterable[Path], bootPaths: Iterable[Path], baseDir: Path): Iterable[Path] =
	{
		def writePaths(name: String, paths: Iterable[Path]): Unit =
			write(baseDir / name asFile, Path.makeString(paths))
		writePaths(AppClassPath, appPaths)
		writePaths(BootClassPath, bootPaths)
		appPaths ++ Seq(baseDir)
	}

	/** The client is responsible for cleaning up the temporary directory.*/
	def makeLoader[T](classpath: Iterable[Path], instance: ScalaInstance): (ClassLoader, Path) =
		makeLoader(classpath, instance.loader, instance)
	/** The client is responsible for cleaning up the temporary directory.*/
	def makeLoader[T](classpath: Iterable[Path], parent: ClassLoader, instance: ScalaInstance): (ClassLoader, Path) =
	{
		val dir = Path.fromFile(createTemporaryDirectory)
		val modifiedClasspath = createClasspathResources(classpath, instance, dir)
		(toLoader(modifiedClasspath, parent), dir)
	}
	
	private[sbt] def printSource(c: Class[_]) =
		println(c.getName + " loader=" +c.getClassLoader + " location=" + FileUtilities.classLocationFile(c))
	
	def isArchive(path: Path): Boolean = isArchive(path.asFile)
	def isArchive(file: File): Boolean = isArchiveName(file.getName)
	def isArchiveName(fileName: String) = fileName.endsWith(".jar") || fileName.endsWith(".zip")
	// Partitions the given classpath into (jars, directories)
	private[sbt] def separate(paths: Iterable[File]): (Iterable[File], Iterable[File]) = paths.partition(isArchive)
	// Partitions the given classpath into (jars, directories)
	private[sbt] def separatePaths(paths: Iterable[Path]) = separate(paths.map(_.asFile.getCanonicalFile))
	private[sbt] def buildSearchPaths(classpath: Iterable[Path]): (wrap.Set[File], wrap.Set[File]) =
	{
		val (jars, dirs) = separatePaths(classpath)
		(linkedSet(jars ++ extraJars.toList), linkedSet(dirs ++ extraDirs.toList))
	}
	private[sbt] def onClasspath(classpathJars: wrap.Set[File], classpathDirectories: wrap.Set[File], file: File): Boolean =
	{
		val f = file.getCanonicalFile
		if(ClasspathUtilities.isArchive(f))
			classpathJars.contains(f)
		else
			classpathDirectories.toList.find(Path.relativize(_, f).isDefined).isDefined
	}
	
	/** Returns all entries in 'classpath' that correspond to a compiler plugin.*/
	private[sbt] def compilerPlugins(classpath: Iterable[Path]): Iterable[File] =
	{
		val loader = new URLClassLoader(Path.getURLs(classpath))
		wrap.Wrappers.toList(loader.getResources("scalac-plugin.xml")).flatMap(asFile(true))
	}
	/** Converts the given URL to a File.  If the URL is for an entry in a jar, the File for the jar is returned. */
	private[sbt] def asFile(url: URL): List[File] = asFile(false)(url)
	private[sbt] def asFile(jarOnly: Boolean)(url: URL): List[File] =
	{
		try
		{
			url.getProtocol match
			{
				case "file" if !jarOnly=> FileUtilities.toFile(url) :: Nil
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
		val bootPaths = FileUtilities.pathSplit(settings.bootclasspath.value).map(p => new File(p)).toList
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
			for(path <- FileUtilities.pathSplit(settings.extdirs.value); val dir = new File(path) if dir.isDirectory)
				findJars(dir)
			buffer.readOnly.map(_.getCanonicalFile)
		}
		(linkedSet(extJars ++ bootJars.map(_.getCanonicalFile)), linkedSet(bootDirs))
	}
	private def linkedSet[T](s: Iterable[T]): wrap.Set[T] =
	{
		val set = new wrap.MutableSetWrapper(new java.util.LinkedHashSet[T])
		set ++= s
		set.readOnly
	}
}

private abstract class LoaderBase(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) with NotNull
{
	require(parent != null) // included because a null parent is legitimate in Java
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		val loaded = findLoadedClass(className)
		val found =
			if(loaded == null)
				doLoadClass(className)
			else
				loaded
			
		if(resolve)
			resolveClass(found)
		found
	}
	protected def doLoadClass(className: String): Class[_]
	protected final def selfLoadClass(className: String): Class[_] = super.loadClass(className, false)
}
private class IntermediateLoader(urls: Array[URL], parent: ClassLoader) extends LoaderBase(urls, parent) with NotNull
{
	def doLoadClass(className: String): Class[_] =
	{
		// if this loader is asked to load an sbt class, it must be because the project we are building is sbt itself,
		 // so we want to load the version of classes on the project classpath, not the parent
		if(className.startsWith(Loaders.SbtPackage))
			findClass(className)
		else
			selfLoadClass(className)
	}
}
/** Delegates class loading to `parent` for all classes included by `filter`.  An attempt to load classes excluded by `filter`
* results in a `ClassNotFoundException`.*/
private class FilteredLoader(parent: ClassLoader, filter: ClassFilter) extends ClassLoader(parent) with NotNull
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
private class SelectiveLoader(urls: Array[URL], parent: ClassLoader, filter: ClassFilter) extends URLClassLoader(urls, parent) with NotNull
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
private trait ClassFilter
{
	def include(className: String): Boolean
}
private abstract class PackageFilter(packages: Iterable[String]) extends ClassFilter
{
	require(packages.forall(_.endsWith(".")))
	protected final def matches(className: String): Boolean = packages.exists(className.startsWith)
}
private class ExcludePackagesFilter(exclude: Iterable[String]) extends PackageFilter(exclude)
{
	def include(className: String): Boolean = !matches(className)
}
private class IncludePackagesFilter(include: Iterable[String]) extends PackageFilter(include)
{
	def include(className: String): Boolean = matches(className)
}

private class LazyFrameworkLoader(baseName: String, urls: Array[URL], parent: ClassLoader, grandparent: ClassLoader)
	extends LoaderBase(urls, parent) with NotNull
{
	def doLoadClass(className: String): Class[_] =
	{
		if(className.startsWith(baseName))
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
	def isSbtClass(className: String) = className.startsWith(Loaders.SbtPackage)
}