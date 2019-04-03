/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.IOException
import java.nio.file.{ DirectoryNotEmptyException, Files, Path }

import sbt.Def._
import sbt.Keys._
import sbt.Project.richInitializeTask
import sbt.io.AllPassFilter
import sbt.io.syntax._
import sbt.nio.Keys._
import sbt.nio.file.{ AnyPath, FileAttributes, FileTreeView, Glob }
import sbt.util.Level

object Clean {

  def deleteContents(file: File, exclude: File => Boolean): Unit =
    deleteContents(
      file.toPath,
      path => exclude(path.toFile),
      FileTreeView.default,
      tryDelete((_: String) => {})
    )
  private[sbt] def deleteContents(
      path: Path,
      exclude: Path => Boolean,
      view: FileTreeView.Nio[FileAttributes],
      delete: Path => Unit
  ): Unit = {
    def deleteRecursive(path: Path): Unit = {
      view
        .list(Glob(path, AnyPath))
        .filterNot { case (p, _) => exclude(p) }
        .foreach {
          case (dir, attrs) if attrs.isDirectory =>
            deleteRecursive(dir)
            delete(dir)
          case (file, _) => delete(file)
        }
    }
    deleteRecursive(path)
  }

  private[this] def cleanFilter(scope: Scope): Def.Initialize[Task[Path => Boolean]] = Def.task {
    val excludes = (cleanKeepFiles in scope).value.map {
      // This mimics the legacy behavior of cleanFilesTask
      case f if f.isDirectory => f * AllPassFilter
      case f                  => f.toGlob
    } ++ (cleanKeepGlobs in scope).value
    p: Path => excludes.exists(_.matches(p))
  }
  private[this] def cleanDelete(scope: Scope): Def.Initialize[Task[Path => Unit]] = Def.task {
    // Don't use a regular logger because the logger actually writes to the target directory.
    val debug = (logLevel in scope).?.value.orElse(state.value.get(logLevel.key)) match {
      case Some(Level.Debug) =>
        (string: String) => println(s"[debug] $string")
      case _ =>
        (_: String) => {}
    }
    tryDelete(debug)
  }

  /**
   * Implements the clean task in a given scope. It uses the outputs task value in the provided
   * scope to determine which files to delete.
   * @param scope the scope in which the clean task is implemented
   * @return the clean task definition.
   */
  def taskIn(scope: Scope): Def.Initialize[Task[Unit]] =
    Def.task {
      val excludes = cleanKeepFiles.value.map {
        // This mimics the legacy behavior of cleanFilesTask
        case f if f.isDirectory => f * AllPassFilter
        case f                  => f.glob
      } ++ cleanKeepGlobs.value
      val excludeFilter: Path => Boolean = p => excludes.exists(_.matches(p))
      // Don't use a regular logger because the logger actually writes to the target directory.
      val debug = (logLevel in scope).?.value.orElse(state.value.get(logLevel.key)) match {
        case Some(Level.Debug) =>
          (string: String) => println(s"[debug] $string")
        case _ =>
          (_: String) => {}
      }
      val delete = tryDelete(debug)
      cleanFiles.value.sorted.reverseIterator.foreach(f => delete(f.toPath))
      (fileOutputs in scope).value.foreach { g =>
        val filter: Path => Boolean = { path =>
          !g.matches(path) || excludeFilter(path)
        }
        deleteContents(g.base, filter, FileTreeView.default, delete)
        delete(g.base)
      }
    } tag Tags.Clean
  private def tryDelete(debug: String => Unit): Path => Unit = path => {
    try {
      debug(s"clean -- deleting file $path")
      Files.deleteIfExists(path)
      ()
    } catch {
      case _: DirectoryNotEmptyException =>
        debug(s"clean -- unable to delete non-empty directory $path")
      case e: IOException =>
        debug(s"Caught unexpected exception $e deleting $path")
    }
  }
}
