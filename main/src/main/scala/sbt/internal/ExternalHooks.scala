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
import sbt.nio.Keys._
import sbt.nio.file.syntax._
import sbt.nio.file.{ FileAttributes, FileTreeView, RecursiveGlob }
import sbt.nio.{ FileChanges, FileStamp, FileStamper }
import sbt.util.InterfaceUtil.jo2o
import xsbti.{ VirtualFile, VirtualFileRef }
import xsbti.compile._
import xsbti.compile.analysis.Stamp

private[sbt] object ExternalHooks {
  private type Func =
    (FileChanges, FileChanges, FileTreeView[(Path, FileAttributes)]) => ExternalHooks
  def default: Def.Initialize[sbt.Task[Func]] = Def.task {
    val unmanagedCache = unmanagedFileStampCache.value
    val managedCache = managedFileStampCache.value
    val cp = dependencyClasspath.value.map(_.data)
    cp.foreach { file =>
      val path = file.toPath
      managedCache.getOrElseUpdate(path, FileStamper.Hash)
    }
    val classGlob = classDirectory.value.toGlob / RecursiveGlob / "*.class"
    val options = (compileOptions in compile).value
    ((inputFileChanges, outputFileChanges, fileTreeView) => {
      fileTreeView.list(classGlob).foreach {
        case (path, _) =>
          s"updating $path"
          managedCache.update(path, FileStamper.Hash)
      }
      apply(inputFileChanges, outputFileChanges, options, unmanagedCache, managedCache)
    }): Func
  }
  private def apply(
      inputFileChanges: FileChanges,
      outputFileChanges: FileChanges,
      options: CompileOptions,
      unmanagedCache: FileStamp.Cache,
      managedCache: FileStamp.Cache
  ): DefaultExternalHooks = {
    val converter = jo2o(options.converter) getOrElse {
      sys.error("file converter was expected")
    }
    val lookup = new ExternalLookup {
      override def changedSources(
          previousAnalysis: CompileAnalysis
      ): Option[Changes[VirtualFileRef]] = Some {
        new Changes[VirtualFileRef] {
          override val getAdded: java.util.Set[VirtualFileRef] =
            new java.util.HashSet[VirtualFileRef]
          override val getRemoved: java.util.Set[VirtualFileRef] =
            new java.util.HashSet[VirtualFileRef]
          override val getChanged: java.util.Set[VirtualFileRef] =
            new java.util.HashSet[VirtualFileRef]
          override val getUnmodified: java.util.Set[VirtualFileRef] =
            new java.util.HashSet[VirtualFileRef]

          override def toString: String =
            s"""Changes(added = $getAdded, removed = $getRemoved, changed = $getChanged, unmodified = ...)"""

          private def add(p: VirtualFileRef, sets: java.util.Set[VirtualFileRef]*): Unit = {
            sets.foreach(add(p, _))
          }
          private def add(f: VirtualFileRef, set: java.util.Set[VirtualFileRef]): Unit = {
            set.add(f); ()
          }
          val allChanges = new java.util.HashSet[VirtualFileRef]
          inputFileChanges match {
            case FileChanges(c, d, m, _) =>
              c.map(converter.toVirtualFile).foreach(add(_, getAdded, allChanges))
              d.map(converter.toVirtualFile).foreach(add(_, getRemoved, allChanges))
              m.map(converter.toVirtualFile).foreach(add(_, getChanged, allChanges))
            case _ =>
          }
          override def isEmpty: java.lang.Boolean =
            getAdded.isEmpty && getRemoved.isEmpty && getChanged.isEmpty

          private val prevSources = previousAnalysis.readStamps().getAllSourceStamps
          prevSources.forEach { (file: VirtualFileRef, s: Stamp) =>
            if (!allChanges.contains(file)) {
              val path: Path = converter.toPath(file)
              unmanagedCache
                .get(path)
                .orElse(managedCache.getOrElseUpdate(path, FileStamper.Hash)) match {
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
      override def hashClasspath(files: Array[VirtualFile]): Optional[Array[FileHash]] =
        Optional.empty[Array[FileHash]]

      import scala.collection.JavaConverters._
      private val javaHome = Option(System.getProperty("java.home")).map(Paths.get(_))
      override def changedBinaries(
          previousAnalysis: CompileAnalysis
      ): Option[Set[VirtualFileRef]] = {
        val base: Set[VirtualFileRef] =
          (outputFileChanges.modified ++ outputFileChanges.created ++ outputFileChanges.deleted)
            .map(converter.toVirtualFile(_))
            .toSet
        Some(base ++ previousAnalysis.readStamps.getAllLibraryStamps.asScala.flatMap {
          case (file, stamp) =>
            val path = converter.toPath(file)
            val stampOpt = managedCache.getOrElseUpdate(path, FileStamper.Hash)
            stampOpt match {
              case Some(s) if equiv(s.stamp, stamp) => None
              case _ =>
                javaHome match {
                  case Some(h) if path.startsWith(h) => None
                  case _ if file.name == "rt.jar"    => None
                  case _                             =>
                    // stampOpt map { s => println(s"stamp changed for $file from ${s.stamp} to $stamp") }
                    Some(file)
                }
            }
        })
      }

      override def removedProducts(
          previousAnalysis: CompileAnalysis
      ): Option[Set[VirtualFileRef]] = {
        None
        Some(previousAnalysis.readStamps.getAllProductStamps.asScala.flatMap {
          case (file, stamp) =>
            val path = converter.toPath(file)
            managedCache.get(path) match {
              case Some(s) if equiv(s.stamp, stamp) => None
              case Some(s)                          => Some(file)
              case _                                =>
                // This shouldn't be necessary
                if (java.nio.file.Files.exists(path)) None
                else Some(file)
            }
        }.toSet)
      }
    }
    new DefaultExternalHooks(Optional.of(lookup), Optional.empty[ClassFileManager])
  }
}
