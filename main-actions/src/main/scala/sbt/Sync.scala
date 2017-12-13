/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.{ File, IOException }
import java.util.zip.ZipException
import sbt.internal.util.Relation
import sbt.internal.io.TranslatedException
import sbt.util.CacheImplicits._
import sbt.util.{ FileInfo, CacheStore }
import sbt.io.IO

import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }

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
  def apply(store: CacheStore,
            inStyle: FileInfo.Style = FileInfo.lastModified,
            outStyle: FileInfo.Style = FileInfo.exists)
    : Traversable[(File, File)] => Relation[File, File] =
    mappings => {
      val relation = Relation.empty ++ mappings
      noDuplicateTargets(relation)
      val currentInfo = relation._1s.map(s => (s, inStyle(s))).toMap

      val (previousRelation, previousInfo) = readInfo(store)(inStyle.format)
      val removeTargets = previousRelation._2s -- relation._2s

      def outofdate(source: File, target: File): Boolean =
        !previousRelation.contains(source, target) ||
          (previousInfo get source) != (currentInfo get source) ||
          !target.exists ||
          target.isDirectory != source.isDirectory

      val updates = relation filter outofdate

      val (cleanDirs, cleanFiles) = (updates._2s ++ removeTargets).partition(_.isDirectory)

      IO.delete(cleanFiles)
      IO.deleteIfEmpty(cleanDirs)
      updates.all.foreach((copy _).tupled)

      writeInfo(store, relation, currentInfo)(inStyle.format)
      relation
    }

  def copy(source: File, target: File): Unit =
    if (source.isFile)
      IO.copyFile(source, target, true)
    else if (!target.exists) // we don't want to update the last modified time of an existing directory
      {
        IO.createDirectory(target)
        IO.copyLastModified(source, target)
      }

  def noDuplicateTargets(relation: Relation[File, File]): Unit = {
    val dups = relation.reverseMap.filter {
      case (_, srcs) =>
        srcs.size >= 2 && srcs.exists(!_.isDirectory)
    } map {
      case (target, srcs) =>
        "\n\t" + target + "\nfrom\n\t" + srcs.mkString("\n\t\t")
    }
    if (dups.nonEmpty)
      sys.error("Duplicate mappings:" + dups.mkString)
  }

  implicit def relationFormat[A, B](implicit af: JsonFormat[Map[A, Set[B]]],
                                    bf: JsonFormat[Map[B, Set[A]]]): JsonFormat[Relation[A, B]] =
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

  def writeInfo[F <: FileInfo](store: CacheStore,
                               relation: Relation[File, File],
                               info: Map[File, F])(implicit infoFormat: JsonFormat[F]): Unit =
    store.write((relation, info))

  type RelationInfo[F] = (Relation[File, File], Map[File, F])

  def readInfo[F <: FileInfo](store: CacheStore)(
      implicit infoFormat: JsonFormat[F]): RelationInfo[F] =
    try { readUncaught[F](store)(infoFormat) } catch {
      case _: IOException  => (Relation.empty[File, File], Map.empty[File, F])
      case _: ZipException => (Relation.empty[File, File], Map.empty[File, F])
      case e: TranslatedException =>
        e.getCause match {
          case _: ZipException => (Relation.empty[File, File], Map.empty[File, F])
          case _               => throw e
        }
    }

  private def readUncaught[F <: FileInfo](store: CacheStore)(
      implicit infoFormat: JsonFormat[F]): RelationInfo[F] =
    store.read(default = (Relation.empty[File, File], Map.empty[File, F]))
}
