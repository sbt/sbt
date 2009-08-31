package xsbt

import java.io.{File, FileNotFoundException}
import sbinary.{DefaultProtocol, Format, Operations}
import scala.reflect.Manifest

object CacheIO
{
	def fromFile[T](format: Format[T], default: => T)(file: File)(implicit mf: Manifest[Format[T]]): T =
		fromFile(file, default)(format, mf)
	def fromFile[T](file: File, default: => T)(implicit format: Format[T], mf: Manifest[Format[T]]): T =
		try { Operations.fromFile(file)(stampedFormat(format)) }
		catch { case e: FileNotFoundException => default }
	def toFile[T](format: Format[T])(value: T)(file: File)(implicit mf: Manifest[Format[T]]): Unit =
		toFile(value)(file)(format, mf)
	def toFile[T](value: T)(file: File)(implicit format: Format[T], mf: Manifest[Format[T]]): Unit =
	{
		FileUtilities.createDirectory(file.getParentFile)
		Operations.toFile(value)(file)(stampedFormat(format))
	}
	def stampedFormat[T](format: Format[T])(implicit mf: Manifest[Format[T]]): Format[T] =
	{
		import DefaultProtocol._
		withStamp(stamp(format))(format)
	}
	def stamp[T](format: Format[T])(implicit mf: Manifest[Format[T]]): Int = typeHash(mf)
	def typeHash[T](implicit mf: Manifest[T]) = mf.toString.hashCode
	def manifest[T](implicit mf: Manifest[T]): Manifest[T] = mf
	def objManifest[T](t: T)(implicit mf: Manifest[T]): Manifest[T] = mf
}