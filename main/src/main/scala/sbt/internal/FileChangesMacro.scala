/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ Path => NioPath }

import sbt.nio.Keys._
import sbt.nio.{ FileChanges, FileStamp }

import scala.annotation.compileTimeOnly
import scala.quoted.*

/**
 * Provides extension methods to `TaskKey[T]` that can be use to fetch the input and output file
 * dependency changes for a task. Nothing in this object is intended to be called directly but,
 * because there are macro definitions, some of the definitions must be public.
 */
object FileChangesMacro:

  // format: off
  extension [A](in: TaskKey[A])
    @compileTimeOnly(
      "`inputFileChanges` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    inline def inputFileChanges: FileChanges =
      ${ FileChangesMacro.changedInputFilesImpl[A]('in) }

    @compileTimeOnly(
      "`outputFileChanges` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    inline def outputFileChanges: FileChanges =
      ${ FileChangesMacro.changedOutputFilesImpl[A]('in) }

    @compileTimeOnly(
      "`inputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    inline def inputFiles: Seq[NioPath] =
      ${ FileChangesMacro.inputFilesImpl[A]('in) }

    @compileTimeOnly(
      "`outputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    inline def outputFiles: Seq[NioPath] =
      ${ FileChangesMacro.outputFilesImpl[A]('in) }
  // format: on
  def changedInputFilesImpl[A: Type](in: Expr[TaskKey[A]])(using qctx: Quotes): Expr[FileChanges] =
    impl[A](
      in = in,
      currentKey = '{ allInputFiles },
      changeKey = '{ changedInputFiles },
      mapKey = '{ inputFileStamps },
    )

  def changedOutputFilesImpl[A: Type](in: Expr[TaskKey[A]])(using qctx: Quotes): Expr[FileChanges] =
    impl[A](
      in = in,
      currentKey = '{ allOutputFiles },
      changeKey = '{ changedOutputFiles },
      mapKey = '{ outputFileStamps },
    )

  def rescope[A](left: TaskKey[?], right: TaskKey[A]): TaskKey[A] =
    Scoped.scopedTask(left.scope.copy(task = Select(left.key)), right.key)

  def rescope[A](left: Scope, right: TaskKey[A]): TaskKey[A] =
    Scoped.scopedTask(left, right.key)

  private def impl[A: Type](
      in: Expr[TaskKey[A]],
      currentKey: Expr[TaskKey[Seq[NioPath]]],
      changeKey: Expr[TaskKey[Seq[(NioPath, FileStamp)] => FileChanges]],
      mapKey: Expr[TaskKey[Seq[(NioPath, FileStamp)]]],
  )(using qctx: Quotes): Expr[FileChanges] =
    val taskScope = getTaskScope[A](in)
    '{
      val ts: Scope = $taskScope
      val changes = rescope[Seq[(NioPath, FileStamp)] => FileChanges](ts, $changeKey).value
      val current = rescope[Seq[NioPath]](ts, $currentKey).value
      import sbt.nio.FileStamp.Formats.given
      val previous =
        Previous.runtimeInEnclosingTask(rescope[Seq[(NioPath, FileStamp)]](ts, $mapKey)).value
      previous.map(changes).getOrElse(FileChanges.noPrevious(current))
    }

  def inputFilesImpl[A: Type](in: Expr[TaskKey[A]])(using qctx: Quotes): Expr[Seq[NioPath]] =
    val ts = getTaskScope[A](in)
    '{ rescope[Seq[NioPath]]($ts, allInputFiles).value }

  def outputFilesImpl[A: Type](in: Expr[TaskKey[A]])(using qctx: Quotes): Expr[Seq[NioPath]] =
    val ts = getTaskScope[A](in)
    '{ rescope[Seq[NioPath]]($ts, allOutputFiles).value }

  private def getTaskScope[A: Type](in: Expr[TaskKey[A]])(using qctx: Quotes): Expr[sbt.Scope] =
    '{
      if $in.scope.task.toOption.isDefined then $in.scope
      else $in.scope.copy(task = sbt.Select($in.key))
    }
end FileChangesMacro
