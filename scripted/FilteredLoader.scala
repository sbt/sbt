/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.test

final class FilteredLoader(parent: ClassLoader) extends ClassLoader(parent) with NotNull
{
	@throws(classOf[ClassNotFoundException])
	override final def loadClass(className: String, resolve: Boolean): Class[_] =
	{
		if(className.startsWith("java.") || className.startsWith("javax."))
			super.loadClass(className, resolve)
		else
			throw new ClassNotFoundException(className)
	}
	override def getResources(name: String) = null
	override def getResource(name: String) = null
}