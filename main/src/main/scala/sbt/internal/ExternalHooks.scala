/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.{ Path, Paths }
import java.util.Optional

import sbt.Def
import sbt.Keys._
import sbt.internal.inc.ExternalLookup
import sbt.internal.inc.Stamp.equivStamp.equiv
import sbt.io.syntax._
import sbt.nio.Keys._
import sbt.nio.file.syntax._
import sbt.nio.file.{ ChangedFiles, RecursiveGlob }
import sbt.nio.{ FileStamp, FileStamper }
import xsbti.compile._
import xsbti.compile.analysis.Stamp

import scala.collection.JavaConverters._

private[sbt] object ExternalHooks {
  private val javaHome = Option(System.getProperty("java.home")).map(Paths.get(_))
  def default: Def.Initialize[sbt.Task[Option[ChangedFiles] => ExternalHooks]] = Def.task {
    val unmanagedCache = unmanagedFileStampCache.value
    val managedCache = managedFileStampCache.value
    val cp = dependencyClasspath.value.map(_.data)
    cp.foreach { file =>
      val path = file.toPath
      managedCache.getOrElseUpdate(path, FileStamper.LastModified)
    }
    val classGlob = classDirectory.value.toGlob / RecursiveGlob / "*.class"
    fileTreeView.value.list(classGlob).foreach {
      case (path, _) => managedCache.update(path, FileStamper.LastModified)
    }
    val options = (compileOptions in compile).value
    apply(_, options, unmanagedCache, managedCache)
  }
  private def apply(
      changedFiles: Option[ChangedFiles],
      options: CompileOptions,
      unmanagedCache: FileStamp.Cache,
      managedCache: FileStamp.Cache
  ): DefaultExternalHooks = {
    val lookup = new ExternalLookup {
      override def changedSources(previousAnalysis: CompileAnalysis): Option[Changes[File]] = Some {
        new Changes[File] {
          val getAdded: java.util.Set[File] = new java.util.HashSet[File]
          val getRemoved: java.util.Set[File] = new java.util.HashSet[File]
          val getChanged: java.util.Set[File] = new java.util.HashSet[File]
          val getUnmodified: java.util.Set[File] = new java.util.HashSet[File]
          private def add(p: Path, sets: java.util.Set[File]*): Unit = {
            sets.foreach(add(p.toFile, _))
          }
          private def add(f: File, set: java.util.Set[File]): Unit = { set.add(f); () }
          val allChanges = new java.util.HashSet[File]
          changedFiles foreach {
            case ChangedFiles(c, d, u) =>
              c.foreach(add(_, getAdded, allChanges))
              d.foreach(add(_, getRemoved, allChanges))
              u.foreach(add(_, getChanged, allChanges))
          }
          override def isEmpty: java.lang.Boolean =
            getAdded.isEmpty && getRemoved.isEmpty && getChanged.isEmpty
          private val prevSources = previousAnalysis.readStamps().getAllSourceStamps
          prevSources.forEach { (file: File, s: Stamp) =>
            if (!allChanges.contains(file)) {
              val path = file.toPath
              unmanagedCache
                .get(path)
                .orElse(managedCache.getOrElseUpdate(file.toPath, FileStamper.Hash)) match {
                case None => add(file, getRemoved)
                case Some(stamp) =>
                  if (equiv(stamp.stamp, s)) add(file, getUnmodified)
                  else add(file, getChanged)
              }
            }
          }
          options.sources.foreach(file => if (!prevSources.containsKey(file)) getAdded.add(file))
        }
      }

      override def shouldDoIncrementalCompilation(
          set: Set[String],
          compileAnalysis: CompileAnalysis
      ): Boolean = true

      // This could use the cache as well, but it would complicate the cache implementation.
      override def hashClasspath(files: Array[File]): Optional[Array[FileHash]] =
        Optional.empty[Array[FileHash]]

      override def changedBinaries(previousAnalysis: CompileAnalysis): Option[Set[File]] = {
        Some(previousAnalysis.readStamps.getAllBinaryStamps.asScala.flatMap {
          case (file, stamp) =>
            managedCache.get(file.toPath) match {
              case Some(cachedStamp) if equiv(cachedStamp.stamp, stamp) => None
              case _ =>
                javaHome match {
                  case Some(h) if file.toPath.startsWith(h) => None
                  case _ if file.getName == "rt.jar"        => None
                  case _                                    => Some(file)
                }
            }
        }.toSet)
      }

      override def removedProducts(previousAnalysis: CompileAnalysis): Option[Set[File]] = {
        Some(previousAnalysis.readStamps.getAllProductStamps.asScala.flatMap {
          case (file, stamp) =>
            managedCache.get(file.toPath) match {
              case Some(s) if equiv(s.stamp, stamp) => None
              case _                                => Some(file)
            }
        }.toSet)
      }
    }
    new DefaultExternalHooks(Optional.of(lookup), Optional.empty[ClassFileManager])
  }
}
