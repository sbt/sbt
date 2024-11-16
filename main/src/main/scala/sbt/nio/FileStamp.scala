/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.io.{ File, IOException }
import java.nio.file.{ Path, Paths }
import java.util.concurrent.ConcurrentHashMap

import sbt.internal.inc.{ EmptyStamp, Stamper, Hash => IncHash, LastModified => IncLastModified }
import sbt.internal.inc.JavaInterfaceUtil.given
import sbt.io.IO
import sbt.nio.file.FileAttributes
import sbt.util.Digest
import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }
import xsbti.compile.analysis.{ Stamp => XStamp }
import xsbti.VirtualFileRef

/**
 * A trait that indicates what file stamping implementation should be used to track the state of
 * a given file. The two choices are [[FileStamper.Hash]] and [[FileStamper.LastModified]].
 */
sealed trait FileStamper

/**
 * Provides implementations of [[FileStamper]].
 */
object FileStamper {

  /**
   * Track files using a hash.
   */
  case object Hash extends FileStamper

  /**
   * Track files using the last modified time.
   */
  case object LastModified extends FileStamper
}

/**
 * Represents the state of a file. This representation is either a hash of the file contents or
 * the last modified time.
 */
sealed trait FileStamp

/**
 * Provides json formatters for [[FileStamp]].
 */
object FileStamp {
  private[sbt] type Id[A] = A

  extension (fileStamp: FileStamp)
    private[sbt] def stamp: XStamp =
      fileStamp match
        case f: FileHashImpl    => f.xstamp
        case LastModified(time) => new IncLastModified(time)
        case _                  => EmptyStamp

  private[sbt] def apply(path: Path, fileStamper: FileStamper): Option[FileStamp] =
    fileStamper match {
      case FileStamper.Hash         => hash(path)
      case FileStamper.LastModified => lastModified(path)
    }
  private[sbt] def apply(stamp: XStamp): Option[FileStamp] = stamp match {
    case lm: IncLastModified => Some(new LastModified(lm.value))
    case s: IncHash          => Some(fromZincStamp(s))
    case _                   => None
  }
  private[sbt] def apply(path: Path, fileAttributes: FileAttributes): Option[FileStamp] =
    try {
      if (fileAttributes.isDirectory) lastModified(path)
      else
        path.toString match {
          case s if s.endsWith(".jar")   => lastModified(path)
          case s if s.endsWith(".class") => lastModified(path)
          case _                         => hash(path)
        }
    } catch {
      case e: IOException => Some(Error(e))
    }
  private[sbt] def hash(string: String): Hash =
    new FileHashImpl(try {
      sbt.internal.inc.Stamp.fromString(string)
    } catch {
      case _: Throwable => EmptyStamp
    })
  private[sbt] def hash(path: Path): Option[Hash] = Stamper.forFarmHashP(path) match {
    case EmptyStamp => None
    case s          => Some(new FileHashImpl(s))
  }
  private[sbt] def fromZincStamp(stamp: XStamp): Hash = new FileHashImpl(stamp)
  private[sbt] def lastModified(path: Path): Option[LastModified] =
    IO.getModifiedTimeOrZero(path.toFile) match {
      case 0 => None
      case l => Some(LastModified(l))
    }
  private class FileHashImpl(val xstamp: XStamp) extends Hash(xstamp.toString)
  private[sbt] sealed abstract case class Hash private[sbt] (hex: String) extends FileStamp
  private[sbt] final case class LastModified private[sbt] (time: Long) extends FileStamp
  private[sbt] final case class Error(exception: IOException) extends FileStamp

  def toDigest(path: Path, stamp: FileStamp): Digest = stamp match
    case f: FileHashImpl =>
      f.xstamp.getHash().toOption match
        case Some(hash) => Digest.sha256Hash(hash.getBytes("UTF-8"))
        case None       => Digest.sha256Hash(path)
    case FileStamp.Hash(hex)       => Digest.sha256Hash(hex.getBytes("UTF-8"))
    case FileStamp.Error(_)        => Digest.zero
    case FileStamp.LastModified(_) => Digest.sha256Hash(path)

  object Formats {
    given seqPathJsonFormatter: JsonFormat[Seq[Path]] =
      asStringArray(_.toString, Paths.get(_))
    given seqFileJsonFormatter: JsonFormat[Seq[File]] =
      asStringArray(_.toString, new File(_))
    given seqVirtualFileRefJsonFormatter: JsonFormat[Seq[VirtualFileRef]] =
      asStringArray(_.id, VirtualFileRef.of)

    given fileJsonFormatter: JsonFormat[File] = fromSeqJsonFormat[File]
    given pathJsonFormatter: JsonFormat[Path] = fromSeqJsonFormat[Path]
    given virtualFileRefJsonFormatter: JsonFormat[VirtualFileRef] =
      fromSeqJsonFormat[VirtualFileRef]

    private def asStringArray[T](toStr: T => String, fromStr: String => T): JsonFormat[Seq[T]] =
      new JsonFormat[Seq[T]] {
        override def write[J](obj: Seq[T], builder: Builder[J]): Unit = {
          builder.beginArray()
          obj.foreach { x => builder.writeString(toStr(x)) }
          builder.endArray()
        }

        override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Seq[T] =
          jsOpt match {
            case Some(js) =>
              val size = unbuilder.beginArray(js)
              val res = (1 to size) map { _ =>
                fromStr(unbuilder.readString(unbuilder.nextElement))
              }
              unbuilder.endArray()
              res
            case None =>
              deserializationError("Expected JsArray but found None")
          }
      }

    private def fromSeqJsonFormat[T](using seqJsonFormat: JsonFormat[Seq[T]]): JsonFormat[T] =
      new JsonFormat[T] {
        override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T =
          seqJsonFormat.read(jsOpt, unbuilder).head

        override def write[J](obj: T, builder: Builder[J]): Unit =
          seqJsonFormat.write(obj :: Nil, builder)
      }

    given seqPathFileStampJsonFormatter: JsonFormat[Seq[(Path, FileStamp)]] =
      new JsonFormat[Seq[(Path, FileStamp)]] {
        override def write[J](obj: Seq[(Path, FileStamp)], builder: Builder[J]): Unit = {
          val (hashes, lastModifiedTimes) = obj.partition(_._2.isInstanceOf[Hash])
          builder.beginObject()
          builder.addField("hashes", hashes.asInstanceOf[Seq[(Path, Hash)]])(
            seqPathHashJsonFormatter
          )
          builder.addField(
            "lastModifiedTimes",
            lastModifiedTimes.asInstanceOf[Seq[(Path, LastModified)]]
          )(seqPathLastModifiedJsonFormatter)
          builder.endObject()
        }

        override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Seq[(Path, FileStamp)] =
          jsOpt match {
            case Some(js) =>
              unbuilder.beginObject(js)
              val hashes = unbuilder.readField("hashes")(using seqPathHashJsonFormatter)
              val lastModifieds =
                unbuilder.readField("lastModifiedTimes")(using seqPathLastModifiedJsonFormatter)
              unbuilder.endObject()
              hashes ++ lastModifieds
            case None =>
              deserializationError("Expected JsObject but found None")
          }
      }
    private[sbt] val seqPathHashJsonFormatter: JsonFormat[Seq[(Path, Hash)]] =
      new JsonFormat[Seq[(Path, Hash)]] {
        override def write[J](obj: Seq[(Path, Hash)], builder: Builder[J]): Unit = {
          builder.beginArray()
          obj.foreach { case (p, h) =>
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
    private[sbt] val seqPathLastModifiedJsonFormatter: JsonFormat[Seq[(Path, LastModified)]] =
      new JsonFormat[Seq[(Path, LastModified)]] {
        override def write[J](obj: Seq[(Path, LastModified)], builder: Builder[J]): Unit = {
          builder.beginArray()
          obj.foreach { case (p, lm) =>
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

  extension (e: Either[FileStamp, FileStamp]) {
    private def value: Option[FileStamp] = if (e == null) None else Some(e.fold(identity, identity))
  }

  private[sbt] class Cache {
    private val underlying = new ConcurrentHashMap[Path, Either[FileStamp, FileStamp]]

    /**
     * Invalidate the cache entry, but don't re-stamp the file until it's actually used
     * in a call to get or update.
     *
     * @param path the file whose stamp we are invalidating
     */
    def invalidate(path: Path): Unit = underlying.get(path) match {
      case Right(s) =>
        underlying.put(path, Left(s))
        ()
      case _ => ()
    }
    def get(path: Path): Option[FileStamp] =
      underlying.get(path) match {
        case null     => None
        case Left(v)  => updateImpl(path, fileStampToStamper(v))
        case Right(v) => Some(v)
      }
    def getOrElseUpdate(path: Path, stamper: FileStamper): Option[FileStamp] =
      underlying.get(path) match {
        case null     => updateImpl(path, stamper)
        case Left(_)  => updateImpl(path, stamper)
        case Right(v) => Some(v)
      }
    def remove(key: Path): Option[FileStamp] = {
      underlying.remove(key).value
    }
    def put(key: Path, fileStamp: FileStamp): Option[FileStamp] =
      underlying.put(key, Right(fileStamp)) match {
        case null => None
        case e    => e.value
      }

    def putIfAbsent(key: Path, stamper: FileStamper): (Option[FileStamp], Option[FileStamp]) = {
      underlying.get(key) match {
        case null     => (None, updateImpl(key, stamper))
        case Right(s) => (Some(s), None)
        case Left(_)  => (None, None)
      }
    }
    def update(key: Path, stamper: FileStamper): (Option[FileStamp], Option[FileStamp]) = {
      underlying.get(key) match {
        case null => (None, updateImpl(key, stamper))
        case v    => (v.value, updateImpl(key, stamper))
      }
    }

    private def fileStampToStamper(stamp: FileStamp): FileStamper = stamp match {
      case _: Hash => FileStamper.Hash
      case _       => FileStamper.LastModified
    }

    private def updateImpl(path: Path, stamper: FileStamper): Option[FileStamp] = {
      val stamp = FileStamp(path, stamper)
      stamp match {
        case None    => underlying.remove(path)
        case Some(s) => underlying.put(path, Right(s))
      }
      stamp
    }

  }
}
