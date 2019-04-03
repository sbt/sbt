/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.nio

import java.io.{ File, IOException }
import java.nio.file.Path
import java.util

import sbt.internal.Repository
import sbt.internal.inc.{ EmptyStamp, Stamper, LastModified => IncLastModified }
import sbt.internal.util.AttributeKey
import sbt.io.IO
import sbt.nio.file.FileAttributes
import sbt.{ Def, Task }
import xsbti.compile.analysis.Stamp

import scala.util.Try

sealed trait FileStamp
object FileStamp {
  private[nio] type Id[T] = T
  private[nio] val attributeMapKey =
    AttributeKey[util.HashMap[Path, (Option[Hash], Option[LastModified])]]("task-attribute-map")
  private[sbt] def fileHashMap: Def.Initialize[Task[Repository[Id, Path, Hash]]] = Def.task {
    val attributeMap = Keys.fileAttributeMap.value
    path: Path =>
      attributeMap.get(path) match {
        case null =>
          val h = hash(path)
          attributeMap.put(path, (Some(h), None))
          h
        case (Some(h), _) => h
        case (None, lm) =>
          val h = hash(path)
          attributeMap.put(path, (Some(h), lm))
          h
      }
  }
  private[sbt] final class StampedFile(path: Path, val stamp: Stamp)
      extends java.io.File(path.toString)
  private[sbt] val stampedFile: ((Path, FileAttributes)) => File = {
    case (p: Path, a: FileAttributes) => new StampedFile(p, apply(p, a).stamp)
  }
  private[sbt] val stamped: File => Stamp = file => {
    val path = file.toPath
    FileAttributes(path).map(apply(path, _).stamp).getOrElse(EmptyStamp)
  }

  private[sbt] implicit class Ops(val fileStamp: FileStamp) {
    private[sbt] def stamp: Stamp = fileStamp match {
      case f: FileHashImpl    => f.xstamp
      case LastModified(time) => new IncLastModified(time)
      case _                  => EmptyStamp
    }
  }

  private[sbt] val extractor: Try[FileStamp] => FileStamp = (_: Try[FileStamp]).getOrElse(Empty)
  private[sbt] val converter: (Path, FileAttributes) => Try[FileStamp] = (p, a) => Try(apply(p, a))
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
  def hash(path: Path): Hash = new FileHashImpl(Stamper.forHash(path.toFile))
  def lastModified(path: Path): LastModified = LastModified(IO.getModifiedTimeOrZero(path.toFile))
  private[this] class FileHashImpl(val xstamp: Stamp) extends Hash(xstamp.getHash.orElse(""))
  sealed abstract case class Hash private[sbt] (hex: String) extends FileStamp
  case class LastModified private[sbt] (time: Long) extends FileStamp
  case class Error(exception: IOException) extends FileStamp
  case object Empty extends FileStamp
}
