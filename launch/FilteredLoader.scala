/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.boot

import BootConfiguration.{IvyPackage, JLinePackagePath, SbtBootPackage, ScalaPackage}
import scala.collection.immutable.Stream

/** A custom class loader to ensure the main part of sbt doesn't load any Scala or
* Ivy classes from the jar containing the loader. */
private[boot] final class BootFilteredLoader(parent: ClassLoader) extends ClassLoader(parent)
{
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		// note that we allow xsbti.* and jline.*
		if(className.startsWith(ScalaPackage) || className.startsWith(IvyPackage) || className.startsWith(SbtBootPackage))
			throw new ClassNotFoundException(className)
		else
			super.loadClass(className, resolve)
	}
	override def getResources(name: String) = if(includeResource(name)) super.getResources(name) else excludedLoader.getResources(name)
	override def getResource(name: String) = if(includeResource(name)) super.getResource(name) else excludedLoader.getResource(name)
	def includeResource(name: String) = name.startsWith(JLinePackagePath)
	// the loader to use when a resource is excluded.  This needs to be at least parent.getParent so that it skips parent.  parent contains
	// resources included in the launcher, which need to be ignored.  Now that launcher can be unrooted (not the application entry point),
	// this needs to be the Java extension loader (the loader with getParent == null)
	private val excludedLoader = Loaders(parent.getParent).head
}

object Loaders
{
	def apply(loader: ClassLoader): Stream[ClassLoader] =
	{
		def loaders(loader: ClassLoader, accum: Stream[ClassLoader]): Stream[ClassLoader] =
			if(loader eq null) accum else loaders(loader.getParent, Stream.cons(loader, accum))
		loaders(getClass.getClassLoader.getParent, Stream.empty)
	}
}