/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package xsbt

import java.io.File

trait PathMapper extends NotNull
{
	def apply(file: File): String
	def apply(files: Set[File]): Iterable[(File,String)] = files.projection.map(f => (f,apply(f)))
}
final case class RelativePathMapper(base: File) extends PMapper(file => FileUtilities.relativize(base, file).getOrElse(file.getPath))
final case object BasicPathMapper extends PMapper(_.getPath)
final case object FlatPathMapper extends PMapper(_.getName)
class PMapper(val f: File => String) extends PathMapper
{
	def apply(file: File): String = f(file)
}
object PathMapper
{
	val basic: PathMapper = BasicPathMapper
	def relativeTo(base: File): PathMapper = RelativePathMapper(base)
	val flat = FlatPathMapper
	def apply(f: File => String): PathMapper = new PMapper(f)
}

trait FileMapper extends NotNull
{
	def apply(file: File): File
	def apply(files: Set[File]): Iterable[(File,File)] = files.projection.map(f => (f,apply(f)))
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