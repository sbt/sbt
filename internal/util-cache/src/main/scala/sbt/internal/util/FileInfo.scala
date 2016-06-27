/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.internal.util

import sbt.io.Hash

import java.io.File
import sjsonnew.{ Builder, deserializationError, JsonFormat, Unbuilder }
import CacheImplicits._

sealed trait FileInfo {
  def file: File
}

sealed trait HashFileInfo extends FileInfo {
  def hash: List[Byte]
}

sealed trait ModifiedFileInfo extends FileInfo {
  def lastModified: Long
}

sealed trait PlainFileInfo extends FileInfo {
  def exists: Boolean
}

sealed trait HashModifiedFileInfo extends HashFileInfo with ModifiedFileInfo
private final case class PlainFile(file: File, exists: Boolean) extends PlainFileInfo
private final case class FileModified(file: File, lastModified: Long) extends ModifiedFileInfo
private final case class FileHash(file: File, hash: List[Byte]) extends HashFileInfo
private final case class FileHashModified(file: File, hash: List[Byte], lastModified: Long) extends HashModifiedFileInfo

object FileInfo {

  sealed trait Style {
    type F <: FileInfo
    implicit val format: JsonFormat[F]

    def apply(file: File): F

    def apply(files: Set[File]): FilesInfo[F] = FilesInfo(files map apply)

    def unapply(info: F): File = info.file

    def unapply(infos: FilesInfo[F]): Set[File] = infos.files map (_.file)
  }

  object full extends Style {
    override type F = HashModifiedFileInfo

    override implicit val format: JsonFormat[HashModifiedFileInfo] = new JsonFormat[HashModifiedFileInfo] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): HashModifiedFileInfo =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val file = unbuilder.readField[File]("file")
            val hash = unbuilder.readField[List[Byte]]("hash")
            val lastModified = unbuilder.readField[Long]("lastModified")
            unbuilder.endObject()
            FileHashModified(file, hash, lastModified)
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: HashModifiedFileInfo, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("file", obj.file)
        builder.addField("hash", obj.hash)
        builder.addField("lastModified", obj.lastModified)
        builder.endObject()
      }
    }

    override implicit def apply(file: File): HashModifiedFileInfo =
      FileHashModified(file.getAbsoluteFile, Hash(file).toList, file.lastModified)
  }

  object hash extends Style {
    override type F = HashFileInfo

    override implicit val format: JsonFormat[HashFileInfo] = new JsonFormat[HashFileInfo] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): HashFileInfo =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val file = unbuilder.readField[File]("file")
            val hash = unbuilder.readField[List[Byte]]("hash")
            unbuilder.endObject()
            FileHash(file, hash)
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: HashFileInfo, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("file", obj.file)
        builder.addField("hash", obj.hash)
        builder.endObject()
      }
    }

    override implicit def apply(file: File): HashFileInfo =
      FileHash(file.getAbsoluteFile, computeHash(file))

    private def computeHash(file: File): List[Byte] =
      try Hash(file).toList
      catch { case _: Exception => Nil }
  }

  object lastModified extends Style {
    override type F = ModifiedFileInfo

    override implicit val format: JsonFormat[ModifiedFileInfo] = new JsonFormat[ModifiedFileInfo] {
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

    override implicit def apply(file: File): ModifiedFileInfo =
      FileModified(file.getAbsoluteFile, file.lastModified)
  }

  object exists extends Style {
    override type F = PlainFileInfo

    override implicit val format: JsonFormat[PlainFileInfo] = new JsonFormat[PlainFileInfo] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): PlainFileInfo =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val file = unbuilder.readField[File]("file")
            val exists = unbuilder.readField[Boolean]("exists")
            unbuilder.endObject()
            PlainFile(file, exists)
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: PlainFileInfo, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("file", obj.file)
        builder.addField("exists", obj.exists)
        builder.endObject()
      }
    }

    override implicit def apply(file: File): PlainFileInfo = {
      val abs = file.getAbsoluteFile
      PlainFile(abs, abs.exists)
    }
  }
}

final case class FilesInfo[F <: FileInfo] private (files: Set[F])
object FilesInfo {
  implicit def format[F <: FileInfo]: JsonFormat[FilesInfo[F]] = implicitly
  def empty[F <: FileInfo]: FilesInfo[F] = FilesInfo(Set.empty[F])
}
