/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.{ Path, Paths }
import java.util.Optional

import sbt.internal.inc.ExternalLookup
import sbt.io.syntax._
import sbt.io.{ AllPassFilter, Glob, TypedPath }
import sbt.Stamped
import xsbti.compile._
import xsbti.compile.analysis.Stamp

import scala.collection.mutable

private[sbt] object ExternalHooks {
  private val javaHome = Option(System.getProperty("java.home")).map(Paths.get(_))
  def apply(options: CompileOptions, repo: FileTree.Repository): DefaultExternalHooks = {
    def listEntries(glob: Glob): Seq[(Path, FileAttributes)] = repo.get(glob)
    import scala.collection.JavaConverters._
    val sources = options.sources()
    val cachedSources = new java.util.HashMap[File, Stamp]
    val converter: File => Stamp = f => Stamped.sourceConverter(TypedPath(f.toPath))
    sources.foreach {
      case sf: Stamped => cachedSources.put(sf, sf.stamp)
      case f: File     => cachedSources.put(f, converter(f))
    }
    val allBinaries = new java.util.HashMap[File, Stamp]
    options.classpath.foreach {
      case f if f.getName.endsWith(".jar") =>
        // This gives us the entry for the path itself, which is necessary if the path is a jar file
        // rather than a directory.
        listEntries(f.toGlob) foreach { case (p, a) => allBinaries.put(p.toFile, a.stamp) }
      case f =>
        listEntries(f ** AllPassFilter) foreach {
          case (p, a) => allBinaries.put(p.toFile, a.stamp)
        }
    }

    val lookup = new ExternalLookup {
      override def changedSources(previousAnalysis: CompileAnalysis): Option[Changes[File]] = Some {
        new Changes[File] {
          val getAdded: java.util.Set[File] = new java.util.HashSet[File]
          val getRemoved: java.util.Set[File] = new java.util.HashSet[File]
          val getChanged: java.util.Set[File] = new java.util.HashSet[File]
          val getUnmodified: java.util.Set[File] = new java.util.HashSet[File]
          override def isEmpty: java.lang.Boolean =
            getAdded.isEmpty && getRemoved.isEmpty && getChanged.isEmpty
          val prevSources: mutable.Map[File, Stamp] =
            previousAnalysis.readStamps().getAllSourceStamps.asScala
          prevSources.foreach {
            case (file: File, s: Stamp) =>
              cachedSources.get(file) match {
                case null =>
                  getRemoved.add(file)
                case stamp =>
                  if ((stamp.getHash.orElse("") == s.getHash.orElse("")) && (stamp.getLastModified
                        .orElse(-1L) == s.getLastModified.orElse(-1L))) {
                    getUnmodified.add(file)
                  } else {
                    getChanged.add(file)
                  }
              }
          }
          sources.foreach(file => if (!prevSources.contains(file)) getAdded.add(file))
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
            allBinaries.get(file) match {
              case null =>
                javaHome match {
                  case Some(h) if file.toPath.startsWith(h) => None
                  case _                                    => Some(file)
                }
              case cachedStamp if stamp == cachedStamp => None
              case _                                   => Some(file)
            }
        }.toSet)
      }

      override def removedProducts(previousAnalysis: CompileAnalysis): Option[Set[File]] = {
        Some(previousAnalysis.readStamps.getAllProductStamps.asScala.flatMap {
          case (file, s) =>
            allBinaries get file match {
              case null => Some(file)
              case stamp if stamp.getLastModified.orElse(0L) != s.getLastModified.orElse(0L) =>
                Some(file)
              case _ => None
            }
        }.toSet)
      }
    }
    new DefaultExternalHooks(Optional.of(lookup), Optional.empty[ClassFileManager])
  }
}
