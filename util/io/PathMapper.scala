/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package xsbt

import java.io.File

trait PathMapper extends NotNull
{
	def apply(file: File): String
}
class PMapper(f: File => String) extends PathMapper
{
	def apply(file: File) = f(file)
}
object PathMapper
{
	val basic = new PMapper(_.getPath)
	def relativeTo(base: File) = new PMapper(file => FileUtilities.relativize(base, file).getOrElse(file.getPath))
	val flat = new PMapper(_.getName)
	def apply(f: File => String) = new PMapper(f)
}

trait FileMapper extends NotNull
{
	def apply(file: File): File
}
class FMapper(f: File => File) extends FileMapper
{
	def apply(file: File) = f(file)
}
object FileMapper
{
	def basic(newDirectory: File) = new FMapper(file => new File(newDirectory, file.getPath))
	def rebase(oldBase: File, newBase: File) =
		new FMapper(file => new File(newBase, FileUtilities.relativize(oldBase, file).getOrElse(error(file + " not a descendent of " + oldBase))))
	def flat(newDirectory: File) = new FMapper(file => new File(newDirectory, file.getName))
	def apply(f: File => File) = new FMapper(f)
}