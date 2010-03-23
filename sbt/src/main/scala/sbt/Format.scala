/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah, David MacIver
 */
package sbt

import java.io.File
import scala.collection.mutable.{HashSet, Set}

trait Format[T] extends NotNull
{
	def toString(t: T): String
	def fromString(s: String): T
}
abstract class SimpleFormat[T] extends Format[T]
{
	def toString(t: T) = t.toString
}
object Format
{
	def path(basePath: Path): Format[Path] = new Format[Path]
	{
		def toString(path: Path) = Path.relativize(basePath.asFile, path.asFile).getOrElse(error("Path " + path + " not in " + basePath))
		def fromString(s: String) = Path.fromString(basePath, s)
	}
	implicit val file: Format[File] = new Format[File]
	{
		def toString(file: File) = file.getAbsolutePath
		def fromString(s: String) = (new File(s)).getAbsoluteFile
	}
	implicit val hash: Format[Array[Byte]] = new Format[Array[Byte]]
	{
		def toString(hash: Array[Byte]) = Hash.toHex(hash)
		def fromString(hash: String) = Hash.fromHex(hash)
	}
	def set[T](implicit format: Format[T]): Format[Set[T]] = new Format[Set[T]]
	{
		def toString(set: Set[T]) = set.toList.map(format.toString).mkString(File.pathSeparator)
		def fromString(s: String) = (new HashSet[T]) ++ FileUtilities.pathSplit(s).map(_.trim).filter(!_.isEmpty).map(format.fromString)
	}
	implicit val string: Format[String] = new SimpleFormat[String] { def fromString(s: String) = s }
	implicit val test: Format[TestDefinition] = new SimpleFormat[TestDefinition]
	{
		def fromString(s: String) = TestParser.parse(s).fold(error, x => x)
	}
}