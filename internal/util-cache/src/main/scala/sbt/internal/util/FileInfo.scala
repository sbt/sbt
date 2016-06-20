/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.internal.util

import java.io.File
import sbinary.{ DefaultProtocol, Format }
import DefaultProtocol._
import scala.reflect.Manifest
import sbt.io.Hash
import sbt.serialization._

sealed trait FileInfo {
  val file: File
}
@directSubclasses(Array(classOf[FileHash], classOf[HashModifiedFileInfo]))
sealed trait HashFileInfo extends FileInfo {
  val hash: List[Byte]
}
object HashFileInfo {
  implicit val pickler: Pickler[HashFileInfo] with Unpickler[HashFileInfo] = PicklerUnpickler.generate[HashFileInfo]
}
@directSubclasses(Array(classOf[FileModified], classOf[HashModifiedFileInfo]))
sealed trait ModifiedFileInfo extends FileInfo {
  val lastModified: Long
}
object ModifiedFileInfo {
  implicit val pickler: Pickler[ModifiedFileInfo] with Unpickler[ModifiedFileInfo] = PicklerUnpickler.generate[ModifiedFileInfo]
}
@directSubclasses(Array(classOf[PlainFile]))
sealed trait PlainFileInfo extends FileInfo {
  def exists: Boolean
}
object PlainFileInfo {
  implicit val pickler: Pickler[PlainFileInfo] with Unpickler[PlainFileInfo] = PicklerUnpickler.generate[PlainFileInfo]
}
@directSubclasses(Array(classOf[FileHashModified]))
sealed trait HashModifiedFileInfo extends HashFileInfo with ModifiedFileInfo
object HashModifiedFileInfo {
  implicit val pickler: Pickler[HashModifiedFileInfo] with Unpickler[HashModifiedFileInfo] = PicklerUnpickler.generate[HashModifiedFileInfo]
}

private[sbt] final case class PlainFile(file: File, exists: Boolean) extends PlainFileInfo
private[sbt] object PlainFile {
  implicit val pickler: Pickler[PlainFile] with Unpickler[PlainFile] = PicklerUnpickler.generate[PlainFile]
}
private[sbt] final case class FileHash(file: File, hash: List[Byte]) extends HashFileInfo
private[sbt] object FileHash {
  implicit val pickler: Pickler[FileHash] with Unpickler[FileHash] = PicklerUnpickler.generate[FileHash]
}
private[sbt] final case class FileModified(file: File, lastModified: Long) extends ModifiedFileInfo
private[sbt] object FileModified {
  implicit val pickler: Pickler[FileModified] with Unpickler[FileModified] = PicklerUnpickler.generate[FileModified]
}
private[sbt] final case class FileHashModified(file: File, hash: List[Byte], lastModified: Long) extends HashModifiedFileInfo
private[sbt] object FileHashModified {
  implicit val pickler: Pickler[FileHashModified] with Unpickler[FileHashModified] = PicklerUnpickler.generate[FileHashModified]
}

object FileInfo {
  implicit def existsInputCache: InputCache[PlainFileInfo] = exists.infoInputCache
  implicit def modifiedInputCache: InputCache[ModifiedFileInfo] = lastModified.infoInputCache
  implicit def hashInputCache: InputCache[HashFileInfo] = hash.infoInputCache
  implicit def fullInputCache: InputCache[HashModifiedFileInfo] = full.infoInputCache
  implicit val pickler: Pickler[FileInfo] with Unpickler[FileInfo] = PicklerUnpickler.generate[FileInfo]

  sealed trait Style {
    type F <: FileInfo
    implicit def apply(file: File): F
    implicit def unapply(info: F): File = info.file
    implicit val format: Format[F]
    import Cache._
    implicit def fileInfoEquiv: Equiv[F] = defaultEquiv
    def infoInputCache: InputCache[F] = basicInput
    implicit def fileInputCache: InputCache[File] = wrapIn[File, F]
  }
  object full extends Style {
    type F = HashModifiedFileInfo
    implicit def apply(file: File): HashModifiedFileInfo = make(file, Hash(file).toList, file.lastModified)
    def make(file: File, hash: List[Byte], lastModified: Long): HashModifiedFileInfo = FileHashModified(file.getAbsoluteFile, hash, lastModified)
    implicit val format: Format[HashModifiedFileInfo] = wrap(f => (f.file, f.hash, f.lastModified), (make _).tupled)
  }
  object hash extends Style {
    type F = HashFileInfo
    implicit def apply(file: File): HashFileInfo = make(file, computeHash(file))
    def make(file: File, hash: List[Byte]): HashFileInfo = FileHash(file.getAbsoluteFile, hash)
    implicit val format: Format[HashFileInfo] = wrap(f => (f.file, f.hash), (make _).tupled)
    private def computeHash(file: File): List[Byte] = try { Hash(file).toList } catch { case e: Exception => Nil }
  }
  object lastModified extends Style {
    type F = ModifiedFileInfo
    implicit def apply(file: File): ModifiedFileInfo = make(file, file.lastModified)
    def make(file: File, lastModified: Long): ModifiedFileInfo = FileModified(file.getAbsoluteFile, lastModified)
    implicit val format: Format[ModifiedFileInfo] = wrap(f => (f.file, f.lastModified), (make _).tupled)
  }
  object exists extends Style {
    type F = PlainFileInfo
    implicit def apply(file: File): PlainFileInfo = make(file)
    def make(file: File): PlainFileInfo = { val abs = file.getAbsoluteFile; PlainFile(abs, abs.exists) }
    implicit val format: Format[PlainFileInfo] = asProduct2[PlainFileInfo, File, Boolean](PlainFile.apply)(x => (x.file, x.exists))
  }
}

final case class FilesInfo[F <: FileInfo] private (files: Set[F])
object FilesInfo {
  sealed abstract class Style {
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
    implicit def filesInputCache: InputCache[Set[File]] = wrapIn[Set[File], FilesInfo[F]]
    implicit def filesInfoEquiv: Equiv[FilesInfo[F]] = defaultEquiv
  }
  private final class BasicStyle[FI <: FileInfo](style: FileInfo.Style { type F = FI })(implicit val manifest: Manifest[Format[FilesInfo[FI]]]) extends Style {
    type F = FI
    val fileStyle: FileInfo.Style { type F = FI } = style
    private implicit val infoFormat: Format[FI] = fileStyle.format
    implicit def apply(files: Set[File]): FilesInfo[F] = FilesInfo(files.map(_.getAbsoluteFile).map(fileStyle.apply))
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
