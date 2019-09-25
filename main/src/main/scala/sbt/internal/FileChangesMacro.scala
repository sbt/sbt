/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ Path => NioPath }

import sbt.nio.Keys._
import sbt.nio.{ FileChanges, FileStamp }

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
      "`inputFileChanges` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def inputFileChanges: FileChanges = macro changedInputFilesImpl[T]
    @compileTimeOnly(
      "`outputFileChanges` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def outputFileChanges: FileChanges = macro changedOutputFilesImpl[T]
    @compileTimeOnly(
      "`inputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def inputFiles: Seq[NioPath] = macro inputFilesImpl[T]
    @compileTimeOnly(
      "`outputFiles` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task."
    )
    def outputFiles: Seq[NioPath] = macro outputFilesImpl[T]
  }
  def changedInputFilesImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FileChanges] = {
    impl[T](c)(
      c.universe.reify(allInputFiles),
      c.universe.reify(changedInputFiles),
      c.universe.reify(inputFileStamps)
    )
  }
  def changedOutputFilesImpl[T: c.WeakTypeTag](
      c: blackbox.Context
  ): c.Expr[FileChanges] = {
    impl[T](c)(
      c.universe.reify(allOutputFiles),
      c.universe.reify(changedOutputFiles),
      c.universe.reify(outputFileStamps)
    )
  }
  private def impl[T: c.WeakTypeTag](
      c: blackbox.Context
  )(
      currentKey: c.Expr[TaskKey[Seq[NioPath]]],
      changeKey: c.Expr[TaskKey[Seq[(NioPath, FileStamp)] => FileChanges]],
      mapKey: c.Expr[TaskKey[Seq[(NioPath, FileStamp)]]]
  ): c.Expr[FileChanges] = {
    import c.universe._
    val taskScope = getTaskScope(c)
    reify {
      val changes = (changeKey.splice in taskScope.splice).value
      val current = (currentKey.splice in taskScope.splice).value
      import sbt.nio.FileStamp.Formats._
      val previous = Previous.runtimeInEnclosingTask(mapKey.splice in taskScope.splice).value
      previous.map(changes).getOrElse(FileChanges.noPrevious(current))
    }
  }
  def inputFilesImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Seq[NioPath]] = {
    val taskKey = getTaskScope(c)
    c.universe.reify((allInputFiles in taskKey.splice).value)
  }
  def outputFilesImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Seq[NioPath]] = {
    val taskKey = getTaskScope(c)
    c.universe.reify((allOutputFiles in taskKey.splice).value)
  }
  private def getTaskScope[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[sbt.Scope] = {
    import c.universe._
    val taskTpe = c.weakTypeOf[TaskKey[T]]
    lazy val err = "Couldn't expand file change macro."
    c.macroApplication match {
      case Select(Apply(_, k :: Nil), _) if k.tpe <:< taskTpe =>
        val expr = c.Expr[TaskKey[T]](k)
        c.universe.reify {
          if (expr.splice.scope.task.toOption.isDefined) expr.splice.scope
          else expr.splice.scope in expr.splice.key
        }
      case _ => c.abort(c.enclosingPosition, err)
    }
  }
}
