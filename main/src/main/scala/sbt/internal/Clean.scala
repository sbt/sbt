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
import sbt.internal.GlobLister._
import sbt.io.AllPassFilter
import sbt.io.syntax._

object Clean {

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
      val excludeFilter: File => Boolean = excludes.toFileFilter.accept
      val globDeletions = (outputs in scope).value.unique.filterNot(excludeFilter)
      val toDelete = cleanFiles.value.filterNot(excludeFilter) match {
        case f @ Seq(_, _*) => (globDeletions ++ f).distinct
        case _              => globDeletions
      }
      val logger = streams.value.log
      toDelete.sorted.reverseIterator.foreach { f =>
        logger.debug(s"clean -- deleting file $f")
        try Files.deleteIfExists(f.toPath)
        catch {
          case _: DirectoryNotEmptyException =>
            logger.debug(s"clean -- unable to delete non-empty directory $f")
          case e: IOException =>
            logger.debug(s"Caught unexpected exception $e deleting $f")
        }
      }
    } tag Tags.Clean
}
