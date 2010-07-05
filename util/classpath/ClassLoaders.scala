/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt
package classpath

import java.io.File
import java.net.{URI, URL, URLClassLoader}

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
class SelfFirstLoader(classpath: Seq[URL], parent: ClassLoader) extends LoaderBase(classpath, parent)
{
	@throws(classOf[ClassNotFoundException])
	override final def doLoadClass(className: String): Class[_] =
	{
		try { findClass(className) }
		catch { case _: ClassNotFoundException => defaultLoadClass(className) }
	}
}