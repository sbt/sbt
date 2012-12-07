/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

object ModuleUtilities
{
	def getObject(className: String, loader: ClassLoader) =
	{
		val obj = Class.forName(className + "$", true, loader)
		val singletonField = obj.getField("MODULE$")
		singletonField.get(null)
	}
}