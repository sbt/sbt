/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.IOException
import java.nio.file.{ DirectoryNotEmptyException, Files }

import sbt.Def._
import sbt.Keys._
import sbt.Project.richInitializeTask
import sbt.io.syntax._
import sbt.io.{ AllPassFilter, FileTreeView, TypedPath }
import sbt.util.Level

object Clean {

  def deleteContents(file: File, exclude: TypedPath => Boolean): Unit =
    deleteContents(file, exclude, FileTreeView.DEFAULT, tryDelete((_: String) => {}))
  def deleteContents(
      file: File,
      exclude: TypedPath => Boolean,
      view: FileTreeView,
      delete: File => Unit
  ): Unit = {
    def deleteRecursive(file: File): Unit = {
      view.list(file * AllPassFilter).filterNot(exclude).foreach {
        case dir if dir.isDirectory =>
          deleteRecursive(dir.toPath.toFile)
          delete(dir.toPath.toFile)
        case f => delete(f.toPath.toFile)
      }
    }
    deleteRecursive(file)
  }

  /**
   * Provides an implementation for the clean task. It delegates to [[taskIn]] using the
   * resolvedScoped key to set the scope.
   * @return the clean task definition.
   */
  def task: Def.Initialize[Task[Unit]] =
    Def.taskDyn(taskIn(Keys.resolvedScoped.value.scope)) tag Tags.Clean

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
        case f                  => f.toGlob
      } ++ cleanKeepGlobs.value
      val excludeFilter: TypedPath => Boolean = excludes.toTypedPathFilter
      // Don't use a regular logger because the logger actually writes to the target directory.
      val debug = (logLevel in scope).?.value.orElse(state.value.get(logLevel.key)) match {
        case Some(Level.Debug) =>
          (string: String) => println(s"[debug] $string")
        case _ =>
          (_: String) => {}
      }
      val delete = tryDelete(debug)
      cleanFiles.value.sorted.reverseIterator.foreach(delete)
      (fileOutputs in scope).value.foreach { g =>
        val filter: TypedPath => Boolean = {
          val globFilter = g.toTypedPathFilter
          tp => !globFilter(tp) || excludeFilter(tp)
        }
        deleteContents(g.base.toFile, filter, FileTreeView.DEFAULT, delete)
        delete(g.base.toFile)
      }
    } tag Tags.Clean
  private def tryDelete(debug: String => Unit): File => Unit = file => {
    try {
      debug(s"clean -- deleting file $file")
      Files.deleteIfExists(file.toPath)
      ()
    } catch {
      case _: DirectoryNotEmptyException =>
        debug(s"clean -- unable to delete non-empty directory $file")
      case e: IOException =>
        debug(s"Caught unexpected exception $e deleting $file")
    }
  }
}
