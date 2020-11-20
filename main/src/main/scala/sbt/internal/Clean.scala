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
import sbt.io.syntax._
import sbt.nio.Keys._
import sbt.nio.file._
import sbt.nio.file.syntax._
import sbt.util.Level
import sjsonnew.JsonFormat

private[sbt] object Clean {

  private[sbt] def deleteContents(file: File, exclude: File => Boolean): Unit =
    deleteContents(
      file.toPath,
      path => exclude(path.toFile),
      FileTreeView.default,
      tryDelete((_: String) => {})
    )
  private[this] def deleteContents(
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
          case (dir, attrs) if attrs.isDirectory && !attrs.isSymbolicLink =>
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
      case f if f.isDirectory => Glob(f, AnyPath)
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
   *
   * @param scope the scope in which the clean task is implemented
   * @return the clean task definition.
   */
  private[sbt] def task(
      scope: Scope,
      full: Boolean
  ): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      val state = Keys.state.value
      val extracted = Project.extract(state)
      val view = (fileTreeView in scope).value
      val manager = streamsManager.value
      Def.task {
        val excludeFilter = cleanFilter(scope).value
        val delete = cleanDelete(scope).value
        val targetDir = (target in scope).?.value.map(_.toPath)

        targetDir.filter(_ => full).foreach(deleteContents(_, excludeFilter, view, delete))
        (cleanFiles in scope).?.value.getOrElse(Nil).foreach { f =>
          deleteContents(f.toPath, excludeFilter, view, delete)
        }

        // This is the special portion of the task where we clear out the relevant streams
        // and file outputs of a task.
        val streamsKey = scope.task.toOption.map(k => ScopedKey(scope.copy(task = Zero), k))
        val stampsKey =
          extracted.structure.data.getDirect(scope, inputFileStamps.key) match {
            case Some(_) => ScopedKey(scope, inputFileStamps.key) :: Nil
            case _       => Nil
          }
        val streamsGlobs =
          (streamsKey.toSeq ++ stampsKey).map(k => manager(k).cacheDirectory.toGlob / **)
        ((fileOutputs in scope).value.filter(g => targetDir.fold(true)(g.base.startsWith)) ++ streamsGlobs)
          .foreach { g =>
            val filter: Path => Boolean = { path =>
              !g.matches(path) || excludeFilter(path)
            }
            deleteContents(g.base, filter, FileTreeView.default, delete)
            delete(g.base)
          }
      }
    } tag Tags.Clean
  private[sbt] trait ToSeqPath[T] {
    def apply(t: T): Seq[Path]
  }
  private[sbt] object ToSeqPath {
    implicit val identitySeqPath: ToSeqPath[Seq[Path]] = identity _
    implicit val seqFile: ToSeqPath[Seq[File]] = _.map(_.toPath)
    implicit val path: ToSeqPath[Path] = _ :: Nil
    implicit val file: ToSeqPath[File] = _.toPath :: Nil
  }
  private[this] implicit class ToSeqPathOps[T](val t: T) extends AnyVal {
    def toSeqPath(implicit toSeqPath: ToSeqPath[T]): Seq[Path] = toSeqPath(t)
  }
  private[sbt] def cleanFileOutputTask[T: JsonFormat: ToSeqPath](
      taskKey: TaskKey[T]
  ): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      val scope = taskKey.scope in taskKey.key
      Def.task {
        val targetDir = (target in scope).value.toPath
        val filter = cleanFilter(scope).value
        // We do not want to inadvertently delete files that are not in the target directory.
        val excludeFilter: Path => Boolean = path => !path.startsWith(targetDir) || filter(path)
        val delete = cleanDelete(scope).value
        val st = streams.in(scope).value
        taskKey.previous.foreach(_.toSeqPath.foreach(p => if (!excludeFilter(p)) delete(p)))
        delete(st.cacheDirectory.toPath / Previous.DependencyDirectory)
      }
    } tag Tags.Clean
  private[this] def tryDelete(debug: String => Unit): Path => Unit = path => {
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
