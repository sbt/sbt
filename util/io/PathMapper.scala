/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package xsbt

import java.io.File

trait PathMapper extends NotNull
{
	def apply(file: File): String
}

object PathMapper
{
	val basic = new FMapper(_.getPath)
	def relativeTo(base: File) = new FMapper(file => FileUtilities.relativize(base, file).getOrElse(file.getPath))
	val flat = new FMapper(_.getName)
	def apply(f: File => String) = new FMapper(f)
}
class FMapper(f: File => String) extends PathMapper
{
	def apply(file: File) = f(file)
}