/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import java.io.{File, IOException}
import sbinary.{DefaultProtocol, Format}
import DefaultProtocol._
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
sealed trait PlainFileInfo extends FileInfo
{
	def exists: Boolean
}
sealed trait HashModifiedFileInfo extends HashFileInfo with ModifiedFileInfo

private final case class PlainFile(file: File, exists: Boolean) extends PlainFileInfo
private final case class FileHash(file: File, hash: List[Byte]) extends HashFileInfo
private final case class FileModified(file: File, lastModified: Long) extends ModifiedFileInfo
private final case class FileHashModified(file: File, hash: List[Byte], lastModified: Long) extends HashModifiedFileInfo

object FileInfo
{
	implicit def existsInputCache: InputCache[PlainFileInfo] = exists.infoInputCache
	implicit def modifiedInputCache: InputCache[ModifiedFileInfo] = lastModified.infoInputCache
	implicit def hashInputCache: InputCache[HashFileInfo] = hash.infoInputCache
	implicit def fullInputCache: InputCache[HashModifiedFileInfo] = full.infoInputCache

	sealed trait Style
	{
		type F <: FileInfo
		implicit def apply(file: File): F
		implicit def unapply(info: F): File = info.file
		implicit val format: Format[F]
		import Cache._
		implicit def fileInfoEquiv: Equiv[F] = defaultEquiv
		def infoInputCache: InputCache[F] = basicInput
		implicit def fileInputCache: InputCache[File] = wrapIn[File,F]
	}
	object full extends Style
	{
		type F = HashModifiedFileInfo
		implicit def apply(file: File): HashModifiedFileInfo = make(file, Hash(file).toList, file.lastModified)
		def make(file: File, hash: List[Byte], lastModified: Long): HashModifiedFileInfo = FileHashModified(file.getAbsoluteFile, hash, lastModified)
		implicit val format: Format[HashModifiedFileInfo] = wrap(f => (f.file, f.hash, f.lastModified), (make _).tupled)
	}
	object hash extends Style
	{
		type F = HashFileInfo
		implicit def apply(file: File): HashFileInfo = make(file, computeHash(file))
		def make(file: File, hash: List[Byte]): HashFileInfo = FileHash(file.getAbsoluteFile, hash)
		implicit val format: Format[HashFileInfo] = wrap(f => (f.file, f.hash), (make _).tupled)
		private def computeHash(file: File): List[Byte] = try { Hash(file).toList } catch { case e: Exception => Nil }
	}
	object lastModified extends Style
	{
		type F = ModifiedFileInfo
		implicit def apply(file: File): ModifiedFileInfo = make(file, file.lastModified)
		def make(file: File, lastModified: Long): ModifiedFileInfo = FileModified(file.getAbsoluteFile, lastModified)
		implicit val format: Format[ModifiedFileInfo] = wrap(f => (f.file, f.lastModified), (make _).tupled)
	}
	object exists extends Style
	{
		type F = PlainFileInfo
		implicit def apply(file: File): PlainFileInfo = make(file)
		def make(file: File): PlainFileInfo = { val abs = file.getAbsoluteFile; PlainFile(abs, abs.exists) }
		implicit val format: Format[PlainFileInfo] = asProduct2[PlainFileInfo, File, Boolean](PlainFile.apply)(x => (x.file, x.exists))
	}
}

final case class FilesInfo[F <: FileInfo] private(files: Set[F])
object FilesInfo
{
	sealed abstract class Style
	{
		type F <: FileInfo
		val fileStyle: FileInfo.Style { type F = Style.this.F }

		//def manifest: Manifest[F] = fileStyle.manifest
		implicit def apply(files: Set[File]): FilesInfo[F]
		implicit def unapply(info: FilesInfo[F]): Set[File] = info.files.map(_.file)
		implicit val formats: Format[FilesInfo[F]]
		val manifest: Manifest[Format[FilesInfo[F]]]
		def empty: FilesInfo[F] = new FilesInfo[F](Set.empty)
		import Cache._
		def infosInputCache: InputCache[FilesInfo[F]] = basicInput
		implicit def filesInputCache: InputCache[Set[File]] = wrapIn[Set[File],FilesInfo[F]]
		implicit def filesInfoEquiv: Equiv[FilesInfo[F]] = defaultEquiv
	}
	private final class BasicStyle[FI <: FileInfo](style: FileInfo.Style { type F  = FI })
		(implicit val manifest: Manifest[Format[FilesInfo[FI]]]) extends Style
	{
		type F = FI
		val fileStyle: FileInfo.Style { type F = FI } = style
		private implicit val infoFormat: Format[FI] = fileStyle.format
		implicit def apply(files: Set[File]): FilesInfo[F] = FilesInfo( files.map(_.getAbsoluteFile).map(fileStyle.apply) )
		implicit val formats: Format[FilesInfo[F]] = wrap(_.files, (fs: Set[F]) => new FilesInfo(fs))
	}
	lazy val full: Style { type F = HashModifiedFileInfo } = new BasicStyle(FileInfo.full)
	lazy val hash: Style { type F = HashFileInfo } = new BasicStyle(FileInfo.hash)
	lazy val lastModified: Style { type F = ModifiedFileInfo } = new BasicStyle(FileInfo.lastModified)
	lazy val exists: Style { type F = PlainFileInfo } = new BasicStyle(FileInfo.exists)

	implicit def existsInputsCache: InputCache[FilesInfo[PlainFileInfo]] = exists.infosInputCache
	implicit def hashInputsCache: InputCache[FilesInfo[HashFileInfo]] = hash.infosInputCache
	implicit def modifiedInputsCache: InputCache[FilesInfo[ModifiedFileInfo]] = lastModified.infosInputCache
	implicit def fullInputsCache: InputCache[FilesInfo[HashModifiedFileInfo]] = full.infosInputCache
}