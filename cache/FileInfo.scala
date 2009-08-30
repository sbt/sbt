package xsbt

import java.io.{File, IOException}
import sbinary.{DefaultProtocol, Format}
import DefaultProtocol._
import Function.tupled
import scala.reflect.Manifest

sealed trait FileInfo extends NotNull
{
	val file: File
}
sealed trait HashFileInfo extends FileInfo
{
	val hash: List[Byte]
}
sealed trait ModifiedFileInfo  extends FileInfo
{
	val lastModified: Long
}
sealed trait HashModifiedFileInfo extends HashFileInfo with ModifiedFileInfo

private final case class FileHash(file: File, hash: List[Byte]) extends HashFileInfo
private final case class FileModified(file: File, lastModified: Long) extends ModifiedFileInfo
private final case class FileHashModified(file: File, hash: List[Byte], lastModified: Long) extends HashModifiedFileInfo

object FileInfo
{
	sealed trait Style extends NotNull
	{
		type F <: FileInfo
		implicit def apply(file: File): F
		implicit def unapply(info: F): File = info.file
		implicit val format: Format[F]
		/*val manifest: Manifest[F]
		def formatManifest: Manifest[Format[F]] = CacheIO.manifest[Format[F]]*/
		import Cache._
		implicit def infoInputCache: InputCache[File] = wrapInputCache[File,F]
		implicit def infoOutputCache: OutputCache[File] = wrapOutputCache[File,F]
	}
	object full extends Style
	{
		type F = HashModifiedFileInfo
		//val manifest: Manifest[F] = CacheIO.manifest[HashModifiedFileInfo]
		implicit def apply(file: File): HashModifiedFileInfo = make(file, Hash(file).toList, file.lastModified)
		def make(file: File, hash: List[Byte], lastModified: Long): HashModifiedFileInfo = FileHashModified(file.getAbsoluteFile, hash, lastModified)
		implicit val format: Format[HashModifiedFileInfo] = wrap(f => (f.file, f.hash, f.lastModified), tupled(make _))
	}
	object hash extends Style
	{
		type F = HashFileInfo
		//val manifest: Manifest[F] = CacheIO.manifest[HashFileInfo]
		implicit def apply(file: File): HashFileInfo = make(file, computeHash(file).toList)
		def make(file: File, hash: List[Byte]): HashFileInfo = FileHash(file.getAbsoluteFile, hash)
		implicit val format: Format[HashFileInfo] = wrap(f => (f.file, f.hash), tupled(make _))
		private def computeHash(file: File) = try { Hash(file) } catch { case e: Exception => Nil }
	}
	object lastModified extends Style
	{
		type F = ModifiedFileInfo
		//val manifest: Manifest[F] = CacheIO.manifest[ModifiedFileInfo]
		implicit def apply(file: File): ModifiedFileInfo = make(file, file.lastModified)
		def make(file: File, lastModified: Long): ModifiedFileInfo = FileModified(file.getAbsoluteFile, lastModified)
		implicit val format: Format[ModifiedFileInfo] = wrap(f => (f.file, f.lastModified), tupled(make _))
	}
}

final case class FilesInfo[F <: FileInfo] private(files: Set[F]) extends NotNull
object FilesInfo
{
	sealed trait Style extends NotNull
	{
		val fileStyle: FileInfo.Style
		type F = fileStyle.F
		//def manifest: Manifest[F] = fileStyle.manifest
		implicit def apply(files: Set[File]): FilesInfo[F]
		implicit def unapply(info: FilesInfo[F]): Set[File] = info.files.map(_.file)
		implicit val formats: Format[FilesInfo[F]]
		val manifest: Manifest[Format[FilesInfo[F]]]
		import Cache._
		implicit def infosInputCache: InputCache[Set[File]] = wrapInputCache[Set[File],FilesInfo[F]]
		implicit def infosOutputCache: OutputCache[Set[File]] = wrapOutputCache[Set[File],FilesInfo[F]]
	}
	private final class BasicStyle[FI <: FileInfo](val fileStyle: FileInfo.Style { type F  = FI })
		(implicit val manifest: Manifest[Format[FilesInfo[FI]]]) extends Style
	{
		private implicit val infoFormat: Format[FI] = fileStyle.format
		implicit def apply(files: Set[File]): FilesInfo[F] = FilesInfo( files.map(_.getAbsoluteFile).map(fileStyle.apply) )
		implicit val formats: Format[FilesInfo[F]] = wrap(_.files, (fs: Set[F]) => new FilesInfo(fs))
	}
	lazy val full: Style = new BasicStyle(FileInfo.full)
	lazy val hash: Style = new BasicStyle(FileInfo.hash)
	lazy val lastModified: Style = new BasicStyle(FileInfo.lastModified)
}