/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.boot

import BootConfiguration.{IvyPackage, SbtBootPackage, ScalaPackage}

/** A custom class loader to ensure the main part of sbt doesn't load any Scala or
* Ivy classes from the jar containing the loader. */
private[boot] final class BootFilteredLoader extends ClassLoader with NotNull
{
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		if(className.startsWith(ScalaPackage) || className.startsWith(IvyPackage) || className.startsWith(SbtBootPackage))
			throw new ClassNotFoundException(className)
		else
			super.loadClass(className, resolve)
	}
}