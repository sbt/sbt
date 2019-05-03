/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.nio.file.Paths
import java.util.Optional

import sbt.Def
import sbt.Keys._
import sbt.internal.inc.{ EmptyStamp, ExternalLookup, Stamper }
import sbt.io.syntax._
import sbt.nio.Keys._
import sbt.nio.file.RecursiveGlob
import sbt.nio.file.syntax._
import xsbti.compile._
import xsbti.compile.analysis.Stamp

import scala.collection.JavaConverters._
import scala.collection.mutable

private[sbt] object ExternalHooks {
  private val javaHome = Option(System.getProperty("java.home")).map(Paths.get(_))
  private[this] implicit class StampOps(val s: Stamp) extends AnyVal {
    def hash: String = s.getHash.orElse("")
    def lastModified: Long = s.getLastModified.orElse(-1L)
  }
  def default: Def.Initialize[sbt.Task[ExternalHooks]] = Def.task {
    val attributeMap = fileAttributeMap.value
    val cp = dependencyClasspath.value.map(_.data)
    cp.foreach { file =>
      val path = file.toPath
      attributeMap.get(path) match {
        case null => attributeMap.put(path, sbt.nio.FileStamp.lastModified(path))
        case _    =>
      }
    }
    val classGlob = classDirectory.value.toGlob / RecursiveGlob / "*.class"
    fileTreeView.value.list(classGlob).foreach {
      case (path, _) => attributeMap.put(path, sbt.nio.FileStamp.lastModified(path))
    }
    apply(
      (compileOptions in compile).value,
      (file: File) => {
        attributeMap.get(file.toPath) match {
          case null => EmptyStamp
          case s    => s.stamp
        }
      }
    )
  }
  private def apply(
      options: CompileOptions,
      attributeMap: File => Stamp
  ): DefaultExternalHooks = {
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
              attributeMap(file) match {
                case null =>
                  getRemoved.add(file)
                case stamp =>
                  val hash = (if (stamp.getHash.isPresent) stamp else Stamper.forHash(file)).hash
                  if (hash == s.hash) {
                    getUnmodified.add(file)
                  } else {
                    getChanged.add(file)
                  }
              }
          }
          options.sources.foreach(file => if (!prevSources.contains(file)) getAdded.add(file))
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
            attributeMap(file) match {
              case cachedStamp if stamp.getLastModified == cachedStamp.getLastModified => None
              case _ =>
                javaHome match {
                  case Some(h) if file.toPath.startsWith(h) => None
                  case _                                    => Some(file)
                }
            }
        }.toSet)
      }

      override def removedProducts(previousAnalysis: CompileAnalysis): Option[Set[File]] = {
        Some(previousAnalysis.readStamps.getAllProductStamps.asScala.flatMap {
          case (file, stamp) =>
            attributeMap(file) match {
              case s if s.getLastModified == stamp.getLastModified => None
              case _                                               => Some(file)
            }
        }.toSet)
      }
    }
    new DefaultExternalHooks(Optional.of(lookup), Optional.empty[ClassFileManager])
  }
}
