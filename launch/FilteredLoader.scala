/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.boot

import BootConfiguration.{IvyPackage, JLinePackagePath, SbtBootPackage, ScalaPackage}

/** A custom class loader to ensure the main part of sbt doesn't load any Scala or
* Ivy classes from the jar containing the loader. */
private[boot] final class BootFilteredLoader(parent: ClassLoader) extends ClassLoader(parent) with NotNull
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
	override def getResources(name: String) = if(includeResource(name)) super.getResources(name) else parent.getParent.getResources(name)
	override def getResource(name: String) = if(includeResource(name)) super.getResource(name) else parent.getParent.getResource(name)
	def includeResource(name: String) = name.startsWith(JLinePackagePath)
}