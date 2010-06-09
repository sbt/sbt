/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import java.net.URL

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
	def normalize(path: String): String = path.replace('/', File.separatorChar).replace('\\', File.separatorChar)
}

import Paths._
trait PathBase extends NotNull
{
	def files: Set[File]
	def urls: Array[URL] = files.toArray[File].map(_.toURI.toURL)
	def x(mapper: PathMapper): Iterable[(File,String)] = mapper(files)
	def x(mapper: FileMapper): Iterable[(File,File)] = mapper(files)
	def *(filter: java.io.FileFilter): Set[File] = files.flatMap(IO.listFiles(filter))
	def **(filter: java.io.FileFilter): Set[File] = files.filter(filter.accept) ++ files.flatMap(_ * AllPassFilter ** filter)
	def *** = **(AllPassFilter)
	def abs = files.map(_.getAbsoluteFile)
	def descendentsExcept(include: java.io.FileFilter, intermediateExclude: java.io.FileFilter): Set[File] =
		(this ** include) -- (this ** intermediateExclude ** include)
}

final class Paths(val files: Set[File]) extends PathBase
{
	def \(subPath: String) = /(subPath)
	def /(subPath: String): Set[File] = files.flatMap { file => val f = file / subPath; if(f.exists) Seq(f)  else Seq() }
}
final class Path(val asFile: File) extends PathBase
{
	def files = Set(asFile)
	def \(subPath: String) = /(subPath)
	def /(subPath: String) = new File(asFile, normalize(subPath))
	def ++(files: Set[File]) = files + asFile
	def ++(file: File) = Set(file, asFile)
}