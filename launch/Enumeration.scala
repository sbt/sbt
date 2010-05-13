/* sbt -- Simple Build Tool
 * Copyright 2008,2009 David MacIver, Mark Harrah
 */
package xsbt.boot

import Pre._

class Enumeration extends NotNull
{
	def elements: List[Value] = members
	private lazy val members: List[Value] =
	{
		val c = getClass
		val correspondingFields = ListMap( c.getDeclaredFields.map(f => (f.getName, f)) : _*)
		c.getMethods.toList flatMap { method =>
			if(method.getParameterTypes.length == 0 && classOf[Value].isAssignableFrom(method.getReturnType))
			{
				for(field <- correspondingFields.get(method.getName) if field.getType == method.getReturnType) yield
					method.invoke(this).asInstanceOf[Value]
			}
			else
				Nil
		}
	}
	def value(s: String) = new Value(s, 0)
	def value(s: String, i: Int) = new Value(s, i)
	class Value(override val toString: String, val id: Int) extends NotNull
	
	def toValue(s: String): Value = elements.find(_.toString == s).getOrElse(error("Expected one of " + elements.mkString(",") + " (got: " + s + ")"))
}