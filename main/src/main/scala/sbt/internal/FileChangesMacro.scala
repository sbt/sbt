/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ Path => NioPath }

import sbt.nio.FileStamp
import sbt.nio.Keys._
import sbt.nio.file.ChangedFiles

import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Provides extension methods to `TaskKey[T]` that can be use to fetch the input and output file
 * dependency changes for a task. Nothing in this object is intended to be called directly but,
 * because there are macro definitions, some of the definitions must be public.
 *
 */
object FileChangesMacro {
  private[sbt] sealed abstract class TaskOps[T](val taskKey: TaskKey[T]) {
    @compileTimeOnly(
      "`changedInputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def changedInputFiles: Option[ChangedFiles] = macro changedInputFilesImpl[T]
    @compileTimeOnly(
      "`changedOutputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def changedOutputFiles: Option[ChangedFiles] = macro changedOutputFilesImpl[T]
    @compileTimeOnly(
      "`inputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def inputFiles: Seq[NioPath] = macro inputFilesImpl[T]
    @compileTimeOnly(
      "`outputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def outputFiles: Seq[NioPath] = macro outputFilesImpl[T]
  }
  def changedInputFilesImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Option[ChangedFiles]] = {
    impl[T](c)(c.universe.reify(changedInputFiles), c.universe.reify(inputFileStamps))
  }
  def changedOutputFilesImpl[T: c.WeakTypeTag](
      c: blackbox.Context
  ): c.Expr[Option[ChangedFiles]] = {
    impl[T](c)(c.universe.reify(changedOutputFiles), c.universe.reify(outputFileStamps))
  }
  private def impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(
      changeKey: c.Expr[TaskKey[Seq[(NioPath, FileStamp)] => Option[ChangedFiles]]],
      mapKey: c.Expr[TaskKey[Seq[(NioPath, FileStamp)]]]
  ): c.Expr[Option[ChangedFiles]] = {
    import c.universe._
    val taskKey = getTaskKey(c)
    reify {
      val changes = (changeKey.splice in taskKey.splice).value
      import sbt.nio.FileStamp.Formats._
      Previous.runtimeInEnclosingTask(mapKey.splice in taskKey.splice).value.flatMap(changes)
    }
  }
  def inputFilesImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Seq[NioPath]] = {
    val taskKey = getTaskKey(c)
    c.universe.reify((allInputFiles in taskKey.splice).value)
  }
  def outputFilesImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Seq[NioPath]] = {
    val taskKey = getTaskKey(c)
    c.universe.reify((allOutputFiles in taskKey.splice).value)
  }
  private def getTaskKey[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TaskKey[T]] = {
    import c.universe._
    val taskTpe = c.weakTypeOf[TaskKey[T]]
    lazy val err = "Couldn't expand file change macro."
    c.Expr[TaskKey[T]](c.macroApplication match {
      case Select(Apply(_, k :: Nil), _) if k.tpe <:< taskTpe => k
      case _                                                  => c.abort(c.enclosingPosition, err)
    })
  }
}
