/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.File

import scala.util.control.NonFatal
import sbt.io.{ Hash, IO }
import sjsonnew.{ arrayFormat => _, _ }
import sbt.nio.file._
import sbt.nio.file.syntax._

sealed trait FileInfo { def file: File }
sealed trait HashFileInfo extends FileInfo {
  @deprecated("Use hashArray instead", "1.3.0")
  def hash: List[Byte] = hashArray.toList
  private[util] def hashArray: Array[Byte]
}
sealed trait ModifiedFileInfo extends FileInfo { def lastModified: Long }
sealed trait PlainFileInfo extends FileInfo { def exists: Boolean }

sealed trait HashModifiedFileInfo extends HashFileInfo with ModifiedFileInfo

object HashFileInfo {
  given format: JsonFormat[HashFileInfo] = FileInfo.hash.format
}
object ModifiedFileInfo {
  given format: JsonFormat[ModifiedFileInfo] = FileInfo.lastModified.format
}
object PlainFileInfo {
  given format: JsonFormat[PlainFileInfo] = FileInfo.exists.format
}
object HashModifiedFileInfo {
  given format: JsonFormat[HashModifiedFileInfo] = FileInfo.full.format
}

private final case class PlainFile(file: File, exists: Boolean) extends PlainFileInfo

private final case class FileModified(file: File, lastModified: Long) extends ModifiedFileInfo

private final case class FileHashArrayRepr(file: File, override val hashArray: Array[Byte])
    extends HashFileInfo {
  override def hashCode(): Int = (file, java.util.Arrays.hashCode(hashArray)).hashCode()
  override def equals(obj: Any): Boolean = obj match {
    case that: FileHashArrayRepr =>
      this.file == that.file && java.util.Arrays.equals(this.hashArray, that.hashArray)
    case _ => false
  }
}

private final case class FileHashModifiedArrayRepr(
    file: File,
    override val hashArray: Array[Byte],
    lastModified: Long
) extends HashModifiedFileInfo

final case class FilesInfo[F <: FileInfo] private[sbt] (files: Set[F])
object FilesInfo {
  def empty[F <: FileInfo]: FilesInfo[F] = FilesInfo(Set.empty[F])

  implicit def format[F <: FileInfo: JsonFormat]: JsonFormat[FilesInfo[F]] =
    projectFormat(_.files, (fs: Set[F]) => FilesInfo(fs))

  def full: FileInfo.Style = FileInfo.full
  def hash: FileInfo.Style = FileInfo.hash
  def lastModified: FileInfo.Style = FileInfo.lastModified
  def exists: FileInfo.Style = FileInfo.exists
}

object FileInfo {

  /**
   * Stores byte arrays as hex encoded strings, but falls back to reading an array of integers,
   * which is how it used to be stored, if that fails.
   */
  given byteArrayFormat: JsonFormat[Array[Byte]] = new JsonFormat[Array[Byte]] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Array[Byte] = {
      jsOpt match {
        case Some(js) =>
          try {
            Hash.fromHex(unbuilder.readString(js))
          } catch {
            case _: DeserializationException =>
              CacheImplicits.arrayFormat[Byte].read(jsOpt, unbuilder)
          }
        case None => Array.empty
      }
    }

    override def write[J](obj: Array[Byte], builder: Builder[J]): Unit = {
      builder.writeString(Hash.toHex(obj))
    }
  }

  sealed trait Style {
    type F <: FileInfo

    implicit def format: JsonFormat[F]
    implicit def formats: JsonFormat[FilesInfo[F]] =
      projectFormat(_.files, (fs: Set[F]) => FilesInfo(fs))

    def apply(file: File): F
    def apply(files: Set[File]): FilesInfo[F] = FilesInfo(files map apply)

    def unapply(info: F): File = info.file
    def unapply(infos: FilesInfo[F]): Set[File] = infos.files map (_.file)
  }

  object full extends Style {
    type F = HashModifiedFileInfo

    given format: JsonFormat[HashModifiedFileInfo] = new JsonFormat[HashModifiedFileInfo] {
      def write[J](obj: HashModifiedFileInfo, builder: Builder[J]) = {
        builder.beginObject()
        builder.addField("file", obj.file.toString)
        builder.addField("hash", obj.hashArray)
        builder.addField("lastModified", obj.lastModified)
        builder.endObject()
      }

      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) = jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val file = unbuilder.readField[String]("file")
          val hash = unbuilder.readField[Array[Byte]]("hash")
          val lastModified = unbuilder.readField[Long]("lastModified")
          unbuilder.endObject()
          FileHashModifiedArrayRepr(new File(file), hash, lastModified)
        case None => deserializationError("Expected JsObject but found None")
      }
    }

    implicit def apply(file: File): HashModifiedFileInfo =
      FileHashModifiedArrayRepr(file.getAbsoluteFile, Hash(file), IO.getModifiedTimeOrZero(file))
    def apply(file: File, hash: Array[Byte], lastModified: Long): HashModifiedFileInfo =
      FileHashModifiedArrayRepr(file.getAbsoluteFile, hash, lastModified)
  }

  object hash extends Style {
    type F = HashFileInfo

    given format: JsonFormat[HashFileInfo] = new JsonFormat[HashFileInfo] {
      def write[J](obj: HashFileInfo, builder: Builder[J]) = {
        builder.beginObject()
        builder.addField("file", obj.file.toString)
        builder.addField("hash", obj.hashArray)
        builder.endObject()
      }

      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) = jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val file = unbuilder.readField[String]("file")
          val hash = unbuilder.readField[Array[Byte]]("hash")
          unbuilder.endObject()
          FileHashArrayRepr(new File(file), hash)
        case None => deserializationError("Expected JsObject but found None")
      }
    }

    implicit def apply(file: File): HashFileInfo =
      FileHashArrayRepr(file.getAbsoluteFile, computeHash(file))
    def apply(file: File, bytes: Array[Byte]): HashFileInfo =
      FileHashArrayRepr(file.getAbsoluteFile, bytes)

    private def computeHash(file: File): Array[Byte] =
      try Hash(file)
      catch { case NonFatal(_) => Array.empty }
  }

  object lastModified extends Style {
    type F = ModifiedFileInfo

    given format: JsonFormat[ModifiedFileInfo] = new JsonFormat[ModifiedFileInfo] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ModifiedFileInfo =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val file = unbuilder.readField[String]("file")
            val lastModified = unbuilder.readField[String]("lastModified").toLong
            unbuilder.endObject()
            FileModified(new File(file), lastModified)
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: ModifiedFileInfo, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField[String]("file", obj.file.toString)
        builder.addField[String]("lastModified", obj.lastModified.toString)
        builder.endObject()
      }
    }

    implicit def apply(file: File): ModifiedFileInfo =
      FileModified(file.getAbsoluteFile, IO.getModifiedTimeOrZero(file))
    def apply(file: File, lastModified: Long): ModifiedFileInfo =
      FileModified(file.getAbsoluteFile, lastModified)

    /**
     * Returns an instance of [[FileModified]] where, for any directory, the maximum last modified
     * time taken from its contents is used rather than the last modified time of the directory
     * itself. The specific motivation was to prevent the doc task from re-running when the modified
     * time changed for a directory classpath but none of the classfiles had changed.
     *
     * @param file
     *   the file or directory
     * @return
     *   the [[FileModified]]
     */
    private[sbt] def fileOrDirectoryMax(file: File): ModifiedFileInfo = {
      val maxLastModified =
        if (file.isDirectory) FileTreeView.default.list(file.toGlob / **).foldLeft(0L) {
          case (max, (path, attributes)) =>
            val lm = if (!attributes.isDirectory) IO.getModifiedTimeOrZero(path.toFile) else 0L
            if (lm > max) lm else max
        }
        else IO.getModifiedTimeOrZero(file)
      FileModified(file, maxLastModified)
    }
  }

  object exists extends Style {
    type F = PlainFileInfo

    given format: JsonFormat[PlainFileInfo] = new JsonFormat[PlainFileInfo] {
      def write[J](obj: PlainFileInfo, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("file", obj.file.toString)
        builder.addField("exists", obj.exists)
        builder.endObject()
      }

      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]) = jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val file = unbuilder.readField[String]("file")
          val exists = unbuilder.readField[Boolean]("exists")
          unbuilder.endObject()
          PlainFile(new File(file), exists)
        case None => deserializationError("Expected JsObject but found None")
      }
    }

    implicit def apply(file: File): PlainFileInfo = {
      val abs = file.getAbsoluteFile
      PlainFile(abs, abs.exists)
    }
    def apply(file: File, exists: Boolean): PlainFileInfo = {
      val abs = file.getAbsoluteFile
      PlainFile(abs, exists)
    }
  }
}
