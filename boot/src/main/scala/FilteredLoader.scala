/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.boot

import BootConfiguration._

/** A custom class loader to ensure the main part of sbt doesn't load any Scala or
* Ivy classes from the jar containing the loader. */
private[boot] final class BootFilteredLoader extends ClassLoader with NotNull
{
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		if(className.startsWith(ScalaPackage) || className.startsWith(IvyPackage))
			throw new ClassNotFoundException(className)
		else
			super.loadClass(className, resolve)
	}
}