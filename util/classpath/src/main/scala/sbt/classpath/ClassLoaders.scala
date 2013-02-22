/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt
package classpath

import java.io.File
import java.net.{URL, URLClassLoader}
import annotation.tailrec

/** This is a starting point for defining a custom ClassLoader.  Override 'doLoadClass' to define
* loading a class that has not yet been loaded.*/
abstract class LoaderBase(urls: Seq[URL], parent: ClassLoader) extends URLClassLoader(urls.toArray, parent)
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
	/** Provides the implementation of finding a class that has not yet been loaded.*/
	protected def doLoadClass(className: String): Class[_]
	/** Provides access to the default implementation of 'loadClass'.*/
	protected final def defaultLoadClass(className: String): Class[_] = super.loadClass(className, false)
}

/** Searches self first before delegating to the parent.*/
final class SelfFirstLoader(classpath: Seq[URL], parent: ClassLoader) extends LoaderBase(classpath, parent)
{
	@throws(classOf[ClassNotFoundException])
	override final def doLoadClass(className: String): Class[_] =
	{
		try { findClass(className) }
		catch { case _: ClassNotFoundException => defaultLoadClass(className) }
	}
}

/** Doesn't load any classes itself, but instead verifies that all classes loaded through `parent` either come from `root` or `classpath`.*/
final class ClasspathFilter(parent: ClassLoader, root: ClassLoader, classpath: Set[File]) extends ClassLoader(parent)
{
	override def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		val c = super.loadClass(className, resolve)
		if(includeLoader(c.getClassLoader, root) || fromClasspath(c))
			c
		else
			throw new ClassNotFoundException(className)
	}
	private[this] def fromClasspath(c: Class[_]): Boolean =
		try { onClasspath(IO.classLocation(c)) }
		catch { case e: RuntimeException => false }

	private[this] def onClasspath(src: URL): Boolean =
		(src eq null) || (
			IO.urlAsFile(src) match {
				case Some(f) => classpath(f)
				case None => false
			}
		)

	override def getResource(name: String): URL = {
		val u = super.getResource(name)
		if(onClasspath(u)) u else null
	}

	override def getResources(name: String): java.util.Enumeration[URL] =
	{
			import collection.convert.WrapAsScala.{enumerationAsScalaIterator => asIt}
			import collection.convert.WrapAsJava.{asJavaEnumeration => asEn}
		val us = super.getResources(name)
		if(us ne null) asEn(asIt(us).filter(onClasspath)) else null
	}

	@tailrec private[this] def includeLoader(c: ClassLoader, base: ClassLoader): Boolean =
		(base ne null) && (
			(c eq base) || includeLoader(c, base.getParent)
		)
}

/** Delegates class loading to `parent` for all classes included by `filter`.  An attempt to load classes excluded by `filter`
* results in a `ClassNotFoundException`.*/
final class FilteredLoader(parent: ClassLoader, filter: ClassFilter) extends ClassLoader(parent)
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
trait ClassFilter
{
	def include(className: String): Boolean
}
abstract class PackageFilter(packages: Iterable[String]) extends ClassFilter
{
	require(packages.forall(_.endsWith(".")))
	protected final def matches(className: String): Boolean = packages.exists(className.startsWith)
}
final class ExcludePackagesFilter(exclude: Iterable[String]) extends PackageFilter(exclude)
{
	def include(className: String): Boolean = !matches(className)
}
final class IncludePackagesFilter(include: Iterable[String]) extends PackageFilter(include)
{
	def include(className: String): Boolean = matches(className)
}

final class NativeCopyConfig(val tempDirectory: File, val explicitLibraries: Seq[File], val searchPaths: Seq[File])
trait NativeCopyLoader extends ClassLoader
{
	protected val config: NativeCopyConfig
	import config._
	
	private[this] val mapped = new collection.mutable.HashMap[String, String]

	override protected def findLibrary(name: String): String =
		synchronized { mapped.getOrElseUpdate(name, findLibrary0(name)) }

	private[this] def findLibrary0(name: String): String =
	{
		val mappedName = System.mapLibraryName(name)
		val explicit = explicitLibraries.filter(_.getName == mappedName).toStream
		val search = searchPaths.toStream flatMap relativeLibrary(mappedName)
		(explicit ++ search).headOption.map(copy).orNull
	}
	private[this] def relativeLibrary(mappedName: String)(base: File): Seq[File] =
	{
		val f = new File(base, mappedName)
		if(f.isFile) f :: Nil else Nil
	}
	private[this] def copy(f: File): String =
	{
		val target = new File(tempDirectory, f.getName)
		IO.copyFile(f, target)
		target.getAbsolutePath
	}
}