/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

final case class ClasspathOptions(bootLibrary: Boolean, compiler: Boolean, extra: Boolean, autoBoot: Boolean)
object ClasspathOptions
{
	def manual = ClasspathOptions(false, false, false, true)
	def auto = ClasspathOptions(true, true, true, true)
}