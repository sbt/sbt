/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

final case class ClasspathOptions(autoBoot: Boolean, compiler: Boolean, extra: Boolean)
object ClasspathOptions
{
	def manual = ClasspathOptions(false, false, false)
	def auto = ClasspathOptions(true, true, true)
}