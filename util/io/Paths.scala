package xsbt

import java.io.File

object Paths
{
	implicit def stringToPath(s: String): Path = new Path(new File(s))
	implicit def fileToPath(f: File): Path = new Path(f)
	implicit def pathToFile(p: Path): File = p.asFile
	implicit def filesToPaths(fs: Set[File]): Paths = new Paths(fs)
	implicit def filesToPaths(fs: Iterable[File]): Paths =
		fs match
		{
			case s: Set[File] => filesToPaths(s)
			case _ => new Paths(Set(fs.toSeq : _*))
		}
}

import Paths._
trait PathBase extends NotNull
{
	def files: Set[File]
	def x(mapper: PathMapper): Iterable[(File,String)] = mapper(files)
	def x(mapper: FileMapper): Iterable[(File,File)] = mapper(files)
	def *(filter: java.io.FileFilter): Set[File] = files.flatMap(FileUtilities.listFiles(filter))
	def **(filter: java.io.FileFilter): Set[File] = files.filter(filter.accept) ++ files.flatMap(_ * AllPassFilter ** filter)
	def *** = **(AllPassFilter)
	def abs = files.map(_.getAbsoluteFile)
	def descendentsExcept(include: java.io.FileFilter, intermediateExclude: java.io.FileFilter): Set[File] =
		(this ** include) -- (this ** intermediateExclude ** include)
}

final class Paths(val files: Set[File]) extends PathBase
{
	def \(subPath: String) = /(subPath)
	def /(subPath: String): Set[File] = files.flatMap(FileUtilities.listFiles)
}
final class Path(val asFile: File) extends PathBase
{
	def files = Set(asFile)
	def \(subPath: String) = /(subPath)
	def /(subPath: String) = new File(asFile, subPath.replace('/', File.separatorChar).replace('\\', File.separatorChar))
	def ++(files: Set[File]) = files + asFile
	def ++(file: File) = Set(file, asFile)
}