/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import java.io.File
import scala.quoted.*
import scala.reflect.ClassTag

import sbt.util.{ NoJsonWriter, OptJsonWriter }
import sbt.internal.util.{ AttributeKey, KeyTag }

private[sbt] object KeyMacro:
  def settingKeyImpl[A1: Type](description: Expr[String])(using Quotes): Expr[SettingKey[A1]] =
    val name = definingValName(errorMsg("settingKey"))
    val tag = '{ KeyTag.Setting[A1](${ summonRuntimeClass[A1] }) }
    val ojw = Expr
      .summon[OptJsonWriter[A1]]
      .getOrElse(errorAndAbort(s"OptJsonWriter[A] not found for ${Type.show[A1]}"))
    '{ SettingKey(AttributeKey($name, $description, Int.MaxValue)(using $tag, $ojw)) }

  def taskKeyImpl[A1: Type](description: Expr[String])(using Quotes): Expr[TaskKey[A1]] =
    val name = definingValName(errorMsg("taskKey"))
    val tag: Expr[KeyTag[Task[A1]]] = Type.of[A1] match
      case '[Seq[a]] =>
        '{ KeyTag.SeqTask(${ summonRuntimeClass[a] }) }
      case _ => '{ KeyTag.Task(${ summonRuntimeClass[A1] }) }
    '{ TaskKey(AttributeKey($name, $description, Int.MaxValue)(using $tag, NoJsonWriter())) }

  def inputKeyImpl[A1: Type](description: Expr[String])(using Quotes): Expr[InputKey[A1]] =
    val name = definingValName(errorMsg("inputTaskKey"))
    val tag: Expr[KeyTag[InputTask[A1]]] = '{ KeyTag.InputTask(${ summonRuntimeClass[A1] }) }
    '{ InputKey(AttributeKey($name, $description, Int.MaxValue)(using $tag, NoJsonWriter())) }

  def projectImpl(using Quotes): Expr[Project] =
    val name = definingValName(errorMsg2)
    '{ Project($name, new File($name)) }

  private def summonRuntimeClass[A: Type](using Quotes): Expr[Class[?]] =
    val classTag = Expr
      .summon[ClassTag[A]]
      .getOrElse(errorAndAbort(s"ClassTag[${Type.show[A]}] not found"))
    '{ $classTag.runtimeClass }

  private def errorAndAbort(msg: String)(using q: Quotes): Nothing =
    q.reflect.report.errorAndAbort(msg)

  private def errorMsg(methodName: String): String =
    s"""$methodName must be directly assigned to a val, such as `val x = $methodName[Int]("description")`."""

  private def errorMsg2: String =
    """project must be directly assigned to a val, such as `val x = project.in(file("core"))`."""

  private[sbt] def definingValName(errorMsg: String)(using qctx: Quotes): Expr[String] =
    val term = enclosingTerm
    if term.isValDef then Expr(term.name)
    else errorAndAbort(errorMsg)

  private[sbt] def callerThis(using Quotes): Expr[Any] =
    import quotes.reflect.*
    This(enclosingClass).asExpr

  private def enclosingTerm(using qctx: Quotes) =
    import qctx.reflect._
    def enclosingTerm0(sym: Symbol): Symbol =
      sym match
        case sym if sym.flags is Flags.Macro => enclosingTerm0(sym.owner)
        case sym if !sym.isTerm              => enclosingTerm0(sym.owner)
        case _                               => sym
    enclosingTerm0(Symbol.spliceOwner)

  private def enclosingClass(using Quotes) =
    import quotes.reflect.*
    def rec(sym: Symbol): Symbol =
      if sym.isClassDef then sym
      else rec(sym.owner)
    rec(Symbol.spliceOwner)
end KeyMacro
