/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.util

import java.io.File
import scala.util.control.NonFatal
import sbt.io.Hash
import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }
import CacheImplicits._

sealed trait FileInfo { def file: File }
sealed trait HashFileInfo extends FileInfo { def hash: List[Byte] }
sealed trait ModifiedFileInfo extends FileInfo { def lastModified: Long }
sealed trait PlainFileInfo extends FileInfo { def exists: Boolean }

sealed trait HashModifiedFileInfo extends HashFileInfo with ModifiedFileInfo

object HashFileInfo {
  implicit val format: JsonFormat[HashFileInfo] = FileInfo.hash.format
}
object ModifiedFileInfo {
  implicit val format: JsonFormat[ModifiedFileInfo] = FileInfo.lastModified.format
}
object PlainFileInfo {
  implicit val format: JsonFormat[PlainFileInfo] = FileInfo.exists.format
}
object HashModifiedFileInfo {
  implicit val format: JsonFormat[HashModifiedFileInfo] = FileInfo.full.format
}

private final case class PlainFile(file: File, exists: Boolean) extends PlainFileInfo
private final case class FileModified(file: File, lastModified: Long) extends ModifiedFileInfo
private final case class FileHash(file: File, hash: List[Byte]) extends HashFileInfo
private final case class FileHashModified(file: File, hash: List[Byte], lastModified: Long) extends HashModifiedFileInfo

final case class FilesInfo[F <: FileInfo] private (files: Set[F])
object FilesInfo {
  def empty[F <: FileInfo]: FilesInfo[F] = FilesInfo(Set.empty[F])

  implicit def format[F <: FileInfo: JsonFormat]: JsonFormat[FilesInfo[F]] =
    project(_.files, (fs: Set[F]) => FilesInfo(fs))

  def full: FileInfo.Style = FileInfo.full
  def hash: FileInfo.Style = FileInfo.hash
  def lastModified: FileInfo.Style = FileInfo.lastModified
  def exists: FileInfo.Style = FileInfo.exists
}

object FileInfo {
  sealed trait Style {
    type F <: FileInfo

    implicit def format: JsonFormat[F]
    implicit def formats: JsonFormat[FilesInfo[F]] = project(_.files, (fs: Set[F]) => FilesInfo(fs))

    def apply(file: File): F
    def apply(files: Set[File]): FilesInfo[F] = FilesInfo(files map apply)

    def unapply(info: F): File = info.file
    def unapply(infos: FilesInfo[F]): Set[File] = infos.files map (_.file)
  }

  object full extends Style {
    type F = HashModifiedFileInfo

    implicit val format: JsonFormat[HashModifiedFileInfo] = new JsonFormat[HashModifiedFileInfo] {
      def write[J](obj: HashModifiedFileInfo, builder: Builder[J]) = {
        builder.beginObject()
        builder.addField("file", obj.file)
        builder.addField("hash", obj.hash)
        builder.addField("lastModified", obj.lastModified)
        builder.endObject()
      }

      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) = jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val file = unbuilder.readField[File]("file")
          val hash = unbuilder.readField[List[Byte]]("hash")
          val lastModified = unbuilder.readField[Long]("lastModified")
          unbuilder.endObject()
          FileHashModified(file, hash, lastModified)
        case None => deserializationError("Expected JsObject but found None")
      }
    }

    implicit def apply(file: File): HashModifiedFileInfo =
      FileHashModified(file.getAbsoluteFile, Hash(file).toList, file.lastModified)
  }

  object hash extends Style {
    type F = HashFileInfo

    implicit val format: JsonFormat[HashFileInfo] = new JsonFormat[HashFileInfo] {
      def write[J](obj: HashFileInfo, builder: Builder[J]) = {
        builder.beginObject()
        builder.addField("file", obj.file)
        builder.addField("hash", obj.hash)
        builder.endObject()
      }

      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) = jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val file = unbuilder.readField[File]("file")
          val hash = unbuilder.readField[List[Byte]]("hash")
          unbuilder.endObject()
          FileHash(file, hash)
        case None => deserializationError("Expected JsObject but found None")
      }
    }

    implicit def apply(file: File): HashFileInfo = FileHash(file.getAbsoluteFile, computeHash(file))

    private def computeHash(file: File): List[Byte] = try Hash(file).toList catch { case NonFatal(_) => Nil }
  }

  object lastModified extends Style {
    type F = ModifiedFileInfo

    implicit val format: JsonFormat[ModifiedFileInfo] = new JsonFormat[ModifiedFileInfo] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ModifiedFileInfo =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val file = unbuilder.readField[File]("file")
            val lastModified = unbuilder.readField[Long]("lastModified")
            unbuilder.endObject()
            FileModified(file, lastModified)
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: ModifiedFileInfo, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("file", obj.file)
        builder.addField("lastModified", obj.lastModified)
        builder.endObject()
      }
    }

    implicit def apply(file: File): ModifiedFileInfo = FileModified(file.getAbsoluteFile, file.lastModified)
  }

  object exists extends Style {
    type F = PlainFileInfo

    implicit val format: JsonFormat[PlainFileInfo] = new JsonFormat[PlainFileInfo] {
      def write[J](obj: PlainFileInfo, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("file", obj.file)
        builder.addField("exists", obj.exists)
        builder.endObject()
      }

      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) = jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val file = unbuilder.readField[File]("file")
          val exists = unbuilder.readField[Boolean]("exists")
          unbuilder.endObject()
          PlainFile(file, exists)
        case None => deserializationError("Expected JsObject but found None")
      }
    }

    implicit def apply(file: File): PlainFileInfo = {
      val abs = file.getAbsoluteFile
      PlainFile(abs, abs.exists)
    }
  }
}
