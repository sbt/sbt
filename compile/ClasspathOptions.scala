/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

final case class ClasspathOptions(bootLibrary: Boolean, compiler: Boolean, extra: Boolean, autoBoot: Boolean, filterLibrary: Boolean)
object ClasspathOptions
{
	def manual = ClasspathOptions(false, false, false, true, false)
	def boot = ClasspathOptions(true, false, false, true, true)
	def repl = auto
	def javac(compiler: Boolean) = new ClasspathOptions(false, compiler, false, false, false)
	def auto = ClasspathOptions(true, true, true, true, true)
}