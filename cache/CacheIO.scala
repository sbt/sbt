package xsbt

import java.io.{File, FileNotFoundException}
import sbinary.{DefaultProtocol, Format, Operations}
import scala.reflect.Manifest

object CacheIO
{
	def toBytes[T](format: Format[T])(value: T)(implicit mf: Manifest[Format[T]]): Array[Byte] =
		toBytes[T](value)(format, mf)
	def toBytes[T](value: T)(implicit format: Format[T], mf: Manifest[Format[T]]): Array[Byte] =
		Operations.toByteArray(value)(stampedFormat(format))
	def fromBytes[T](format: Format[T], default: => T)(bytes: Array[Byte])(implicit mf: Manifest[Format[T]]): T =
		fromBytes(default)(bytes)(format, mf)
	def fromBytes[T](default: => T)(bytes: Array[Byte])(implicit format: Format[T], mf: Manifest[Format[T]]): T =
		if(bytes.isEmpty) default else Operations.fromByteArray(bytes)(stampedFormat(format))
		
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