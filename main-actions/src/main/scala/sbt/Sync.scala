/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, IOException }
import java.util.zip.ZipException

import sbt.internal.inc.MappedFileConverter
import sbt.internal.util.Relation
import sbt.internal.io.TranslatedException
import sbt.util.CacheImplicits._
import sbt.util.{ CacheStore, FileInfo }
import sbt.io.IO
import sjsonnew.{
  Builder,
  IsoString,
  IsoStringLong,
  JsonFormat,
  PathOnlyFormats,
  Unbuilder,
  deserializationError,
}
import xsbti.{ FileConverter, VirtualFileRef }

/**
 * Maintains a set of mappings so that they are uptodate.
 * Specifically, 'apply' applies the mappings by creating target directories and copying source files to their destination.
 * For each mapping no longer present, the old target is removed.
 * Caution: Existing files are overwritten.
 * Caution: The removal of old targets assumes that nothing else has written to or modified those files.
 * It tries not to obliterate large amounts of data by only removing previously tracked files and empty directories.
 * That is, it won't remove a directory with unknown (untracked) files in it.
 * Warning: It is therefore inappropriate to use this with anything other than an automatically managed destination or a dedicated target directory.
 * Warning: Specifically, don't mix this with a directory containing manually created files, like sources.
 * It is safe to use for its intended purpose: copying resources to a class output directory.
 */
object Sync {
  @deprecated("Use sync, which doesn't take the unused outStyle param", "1.1.1")
  def apply(
      store: CacheStore,
      inStyle: FileInfo.Style = FileInfo.lastModified,
      outStyle: FileInfo.Style = FileInfo.exists
  ): Iterable[(File, File)] => Relation[File, File] =
    sync(store, inStyle)

  def sync(
      store: CacheStore,
      fileConverter: FileConverter
  ): Iterable[(File, File)] => Relation[File, File] =
    sync(store, FileInfo.lastModified, fileConverter)

  def sync(
      store: CacheStore,
      inStyle: FileInfo.Style = FileInfo.lastModified,
  ): Iterable[(File, File)] => Relation[File, File] =
    sync(store, inStyle, MappedFileConverter.empty)

  /** this function ensures that the latest files in /src are also in /target, so that they are synchronised */
  def sync(
      store: CacheStore,
      inStyle: FileInfo.Style,
      fileConverter: FileConverter
  ): Iterable[(File, File)] => Relation[File, File] =
    mappings => {
      val relation = Relation.empty ++ mappings
      noDuplicateTargets(relation)
      val currentInfo = relation._1s.map(s => (s, inStyle(s))).toMap

      val (previousRelation, previousInfo) =
        readInfoWrapped(store, fileConverter)(using inStyle.format)
      val removeTargets = previousRelation._2s -- relation._2s

      def outofdate(source: File, target: File): Boolean =
        !previousRelation.contains(source, target) ||
          (previousInfo get source) != (currentInfo get source) ||
          !target.exists ||
          target.isDirectory != source.isDirectory

      val updates = relation.filter(outofdate)

      val (cleanDirs, cleanFiles) = (updates._2s ++ removeTargets).partition(_.isDirectory)

      IO.delete(cleanFiles)
      IO.deleteIfEmpty(cleanDirs)
      updates.all.foreach((copy).tupled)

      writeInfoVirtual(store, relation, currentInfo, fileConverter)(using inStyle.format)
      relation
    }

  def copy(source: File, target: File): Unit =
    if (source.isFile) IO.copyFile(source, target, true)
    else if (!target.exists) { // we don't want to update the last modified time of an existing directory
      IO.createDirectory(target)
      IO.copyLastModified(source, target)
      ()
    }

  def noDuplicateTargets(relation: Relation[File, File]): Unit = {
    val dups = relation.reverseMap
      .withFilter { case (_, srcs) => srcs.size >= 2 && srcs.exists(!_.isDirectory) }
      .map { case (target, srcs) => "\n\t" + target + "\nfrom\n\t" + srcs.mkString("\n\t\t") }
    if (dups.nonEmpty)
      sys.error("Duplicate mappings:" + dups.mkString)
  }

  implicit def relationFormat[A, B](using
      af: JsonFormat[Map[A, Set[B]]],
      bf: JsonFormat[Map[B, Set[A]]]
  ): JsonFormat[Relation[A, B]] =
    new JsonFormat[Relation[A, B]] {
      def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Relation[A, B] =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginArray(js)
            val jForward = unbuilder.nextElement
            val jReverse = unbuilder.nextElement
            unbuilder.endArray()
            Relation.make(af.read(Some(jForward), unbuilder), bf.read(Some(jReverse), unbuilder))
          case None =>
            deserializationError("Expected JsArray but found None")
        }

      def write[J](obj: Relation[A, B], builder: Builder[J]): Unit = {
        builder.beginArray()
        af.write(obj.forwardMap, builder)
        bf.write(obj.reverseMap, builder)
        builder.endArray()
      }
    }

  private lazy val fileIsoString: IsoString[File] =
    val iso = summon[IsoStringLong[File]]
    IsoString.iso(
      (file: File) => iso.to(file)._1,
      (s: String) => iso.from((s, 0)),
    )

  def writeInfo[F <: FileInfo](
      store: CacheStore,
      relation: Relation[File, File],
      info: Map[File, F]
  )(using infoFormat: JsonFormat[F]): Unit =
    given IsoString[File] = fileIsoString
    import PathOnlyFormats.given
    store.write((relation, info))

  def writeInfoVirtual[F <: FileInfo](
      store: CacheStore,
      relation: Relation[File, File],
      info: Map[File, F],
      fileConverter: FileConverter
  )(using infoFormat: JsonFormat[F]): Unit = {
    val virtualRelation: Relation[VirtualFileRef, VirtualFileRef] =
      Relation.switch(relation, (f: File) => fileConverter.toVirtualFile(f.toPath))
    val virtualInfo: Map[VirtualFileRef, F] = info.map { case (file, fileInfo) =>
      fileConverter.toVirtualFile(file.toPath) -> fileInfo
    }

    import sjsonnew.IsoString
    implicit def virtualFileRefStringIso: IsoString[VirtualFileRef] =
      IsoString.iso[VirtualFileRef](_.toString, VirtualFileRef.of(_))
    store.write(
      (
        virtualRelation,
        virtualInfo
      )
    )
  }

  type RelationInfo[F] = (Relation[File, File], Map[File, F])
  type RelationInfoVirtual[F] = (Relation[VirtualFileRef, VirtualFileRef], Map[VirtualFileRef, F])

  def readInfoWrapped[F <: FileInfo](store: CacheStore, fileConverter: FileConverter)(using
      infoFormat: JsonFormat[F]
  ): RelationInfo[F] = {
    convertFromVirtual(readInfoVirtual(store)(using infoFormat), fileConverter)
  }

  def convertFromVirtual[F <: FileInfo](
      info: RelationInfoVirtual[F],
      fileConverter: FileConverter
  ): RelationInfo[F] = {
    val firstPart = Relation.switch(info._1, (r: VirtualFileRef) => fileConverter.toPath(r).toFile)
    val secondPart = info._2.map { case (file, fileInfo) =>
      fileConverter.toPath(file).toFile -> fileInfo
    }
    firstPart -> secondPart
  }

  def readInfo[F <: FileInfo](
      store: CacheStore
  )(using infoFormat: JsonFormat[F]): RelationInfo[F] =
    try {
      readUncaught[F](store)(using infoFormat)
    } catch {
      case _: IOException  => (Relation.empty[File, File], Map.empty[File, F])
      case _: ZipException => (Relation.empty[File, File], Map.empty[File, F])
      case e: TranslatedException =>
        e.getCause match {
          case _: ZipException => (Relation.empty[File, File], Map.empty[File, F])
          case _               => throw e
        }
    }

  def readInfoVirtual[F <: FileInfo](
      store: CacheStore
  )(using infoFormat: JsonFormat[F]): RelationInfoVirtual[F] =
    try {
      readUncaughtVirtual[F](store)(using infoFormat)
    } catch {
      case _: IOException =>
        (Relation.empty[VirtualFileRef, VirtualFileRef], Map.empty[VirtualFileRef, F])
      case _: ZipException =>
        (Relation.empty[VirtualFileRef, VirtualFileRef], Map.empty[VirtualFileRef, F])
      case e: TranslatedException =>
        e.getCause match {
          case _: ZipException =>
            (Relation.empty[VirtualFileRef, VirtualFileRef], Map.empty[VirtualFileRef, F])
          case _ => throw e
        }
    }

  private def readUncaught[F <: FileInfo](
      store: CacheStore
  )(using infoFormat: JsonFormat[F]): RelationInfo[F] =
    given IsoString[File] = fileIsoString
    import PathOnlyFormats.given
    store.read(default = (Relation.empty[File, File], Map.empty[File, F]))

  private def readUncaughtVirtual[F <: FileInfo](
      store: CacheStore
  )(using infoFormat: JsonFormat[F]): RelationInfoVirtual[F] = {
    import sjsonnew.IsoString
    implicit def virtualFileRefStringIso: IsoString[VirtualFileRef] =
      IsoString.iso[VirtualFileRef](_.toString, VirtualFileRef.of(_))
    store.read(default =
      (Relation.empty[VirtualFileRef, VirtualFileRef], Map.empty[VirtualFileRef, F])
    )
  }
}
