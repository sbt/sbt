/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.io.{ File, IOException }
import java.nio.file.{ Path, Paths }

import sbt.internal.inc.{ EmptyStamp, Stamper, LastModified => IncLastModified }
import sbt.io.IO
import sbt.nio.file.FileAttributes
import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }
import xsbti.compile.analysis.{ Stamp => XStamp }

import scala.util.Try

sealed trait FileStamper
object FileStamper {
  case object Hash extends FileStamper
  case object LastModified extends FileStamper
}
private[sbt] sealed trait FileStamp

private[sbt] object FileStamp {
  private[sbt] type Id[T] = T

  private[sbt] implicit class Ops(val fileStamp: FileStamp) {
    private[sbt] def stamp: XStamp = fileStamp match {
      case f: FileHashImpl    => f.xstamp
      case LastModified(time) => new IncLastModified(time)
      case _                  => EmptyStamp
    }
  }

  private[sbt] val converter: (Path, FileAttributes) => Try[FileStamp] = (p, a) => Try(apply(p, a))
  def apply(path: Path, fileStamper: FileStamper): FileStamp = fileStamper match {
    case FileStamper.Hash         => hash(path)
    case FileStamper.LastModified => lastModified(path)
  }
  def apply(path: Path, fileAttributes: FileAttributes): FileStamp =
    try {
      if (fileAttributes.isDirectory) lastModified(path)
      else
        path.toString match {
          case s if s.endsWith(".jar")   => lastModified(path)
          case s if s.endsWith(".class") => lastModified(path)
          case _                         => hash(path)
        }
    } catch {
      case e: IOException => Error(e)
    }
  def hash(string: String): Hash = new FileHashImpl(sbt.internal.inc.Hash.unsafeFromString(string))
  def hash(path: Path): Hash = new FileHashImpl(Stamper.forHash(path.toFile))
  def lastModified(path: Path): LastModified = LastModified(IO.getModifiedTimeOrZero(path.toFile))
  private[this] class FileHashImpl(val xstamp: XStamp) extends Hash(xstamp.getHash.orElse(""))
  sealed abstract case class Hash private[sbt] (hex: String) extends FileStamp
  final case class LastModified private[sbt] (time: Long) extends FileStamp
  final case class Error(exception: IOException) extends FileStamp

  implicit val pathJsonFormatter: JsonFormat[Seq[Path]] = new JsonFormat[Seq[Path]] {
    override def write[J](obj: Seq[Path], builder: Builder[J]): Unit = {
      builder.beginArray()
      obj.foreach { path =>
        builder.writeString(path.toString)
      }
      builder.endArray()
    }

    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Seq[Path] =
      jsOpt match {
        case Some(js) =>
          val size = unbuilder.beginArray(js)
          val res = (1 to size) map { _ =>
            Paths.get(unbuilder.readString(unbuilder.nextElement))
          }
          unbuilder.endArray()
          res
        case None =>
          deserializationError("Expected JsArray but found None")
      }
  }

  implicit val fileJsonFormatter: JsonFormat[Seq[File]] = new JsonFormat[Seq[File]] {
    override def write[J](obj: Seq[File], builder: Builder[J]): Unit = {
      builder.beginArray()
      obj.foreach { file =>
        builder.writeString(file.toString)
      }
      builder.endArray()
    }

    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Seq[File] =
      jsOpt match {
        case Some(js) =>
          val size = unbuilder.beginArray(js)
          val res = (1 to size) map { _ =>
            new File(unbuilder.readString(unbuilder.nextElement))
          }
          unbuilder.endArray()
          res
        case None =>
          deserializationError("Expected JsArray but found None")
      }
  }
  implicit val fileJson: JsonFormat[File] = new JsonFormat[File] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): File =
      fileJsonFormatter.read(jsOpt, unbuilder).head
    override def write[J](obj: File, builder: Builder[J]): Unit =
      fileJsonFormatter.write(obj :: Nil, builder)
  }
  implicit val pathJson: JsonFormat[Path] = new JsonFormat[Path] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Path =
      pathJsonFormatter.read(jsOpt, unbuilder).head
    override def write[J](obj: Path, builder: Builder[J]): Unit =
      pathJsonFormatter.write(obj :: Nil, builder)
  }
  implicit val fileStampJsonFormatter: JsonFormat[Seq[(Path, FileStamp)]] =
    new JsonFormat[Seq[(Path, FileStamp)]] {
      override def write[J](obj: Seq[(Path, FileStamp)], builder: Builder[J]): Unit = {
        val (hashes, lastModifiedTimes) = obj.partition(_._2.isInstanceOf[Hash])
        builder.beginObject()
        builder.addField("hashes", hashes.asInstanceOf[Seq[(Path, Hash)]])(fileHashJsonFormatter)
        builder.addField(
          "lastModifiedTimes",
          lastModifiedTimes.asInstanceOf[Seq[(Path, LastModified)]]
        )(
          fileLastModifiedJsonFormatter
        )
        builder.endObject()
      }

      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Seq[(Path, FileStamp)] =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val hashes = unbuilder.readField("hashes")(fileHashJsonFormatter)
            val lastModifieds =
              unbuilder.readField("lastModifiedTimes")(fileLastModifiedJsonFormatter)
            unbuilder.endObject()
            hashes ++ lastModifieds
          case None =>
            deserializationError("Expected JsObject but found None")
        }
    }
  val fileHashJsonFormatter: JsonFormat[Seq[(Path, Hash)]] =
    new JsonFormat[Seq[(Path, Hash)]] {
      override def write[J](obj: Seq[(Path, Hash)], builder: Builder[J]): Unit = {
        builder.beginArray()
        obj.foreach {
          case (p, h) =>
            builder.beginArray()
            builder.writeString(p.toString)
            builder.writeString(h.hex)
            builder.endArray()
        }
        builder.endArray()
      }

      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Seq[(Path, Hash)] =
        jsOpt match {
          case Some(js) =>
            val size = unbuilder.beginArray(js)
            val res = (1 to size) map { _ =>
              unbuilder.beginArray(unbuilder.nextElement)
              val path = Paths.get(unbuilder.readString(unbuilder.nextElement))
              val hash = FileStamp.hash(unbuilder.readString(unbuilder.nextElement))
              unbuilder.endArray()
              path -> hash
            }
            unbuilder.endArray()
            res
          case None =>
            deserializationError("Expected JsArray but found None")
        }
    }
  val fileLastModifiedJsonFormatter: JsonFormat[Seq[(Path, LastModified)]] =
    new JsonFormat[Seq[(Path, LastModified)]] {
      override def write[J](obj: Seq[(Path, LastModified)], builder: Builder[J]): Unit = {
        builder.beginArray()
        obj.foreach {
          case (p, lm) =>
            builder.beginArray()
            builder.writeString(p.toString)
            builder.writeLong(lm.time)
            builder.endArray()
        }
        builder.endArray()
      }

      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Seq[(Path, LastModified)] =
        jsOpt match {
          case Some(js) =>
            val size = unbuilder.beginArray(js)
            val res = (1 to size) map { _ =>
              unbuilder.beginArray(unbuilder.nextElement)
              val path = Paths.get(unbuilder.readString(unbuilder.nextElement))
              val hash = FileStamp.LastModified(unbuilder.readLong(unbuilder.nextElement))
              unbuilder.endArray()
              path -> hash
            }
            unbuilder.endArray()
            res
          case None =>
            deserializationError("Expected JsArray but found None")
        }
    }

}
