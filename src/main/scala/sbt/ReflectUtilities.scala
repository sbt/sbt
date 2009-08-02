/* sbt -- Simple Build Tool
 * Copyright 2008 David MacIver, Mark Harrah
 */
package sbt;

import scala.collection._

object ReflectUtilities
{
	def transformCamelCase(name: String, separator: Char) =
	{
		val buffer = new StringBuilder
		for(char <- name)
		{
			import java.lang.Character._
			if(isUpperCase(char))
			{
				buffer += separator
				buffer += toLowerCase(char)
			}
			else
				buffer += char
		}
		buffer.toString
	}

	def ancestry(clazz : Class[_]) : List[Class[_]] = 
		if (clazz == classOf[AnyRef] || !classOf[AnyRef].isAssignableFrom(clazz)) List(clazz)
		else clazz :: ancestry(clazz.getSuperclass);

	def fields(clazz : Class[_]) = 
		mutable.OpenHashMap(ancestry(clazz).
			flatMap(_.getDeclaredFields).
			map(f => (f.getName, f)):_*)
	
	def allValsC[T](self: AnyRef, clazz: Class[T]): Map[String, T] =
	{
		val mappings = new mutable.OpenHashMap[String, T]
		val correspondingFields = fields(self.getClass)
		for(method <- self.getClass.getMethods)
		{
			if(method.getParameterTypes.length == 0 && clazz.isAssignableFrom(method.getReturnType))
			{
				for(field <- correspondingFields.get(method.getName) if field.getType == method.getReturnType)
				{
					val value = method.invoke(self).asInstanceOf[T]
					assume(value != null, "val " + method.getName + " was null")
					mappings(method.getName) = value
				}
			}
		}
		mappings
	}
	def allVals[T](self: AnyRef)(implicit mt: scala.reflect.Manifest[T]): Map[String, T] =
		allValsC(self, mt.erasure).asInstanceOf[Map[String,T]]
}
