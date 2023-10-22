/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import java.io.File
import scala.annotation.tailrec
import scala.quoted.*
import scala.reflect.ClassTag

import sbt.util.OptJsonWriter

private[sbt] object KeyMacro:
  def settingKeyImpl[A1: Type](
      description: Expr[String]
  )(using qctx: Quotes): Expr[SettingKey[A1]] =
    keyImpl2[A1, SettingKey[A1]]("settingKey") { (name, mf, ojw) =>
      val n = Expr(name)
      '{
        SettingKey[A1]($n, $description)($mf, $ojw)
      }
    }

  def taskKeyImpl[A1: Type](description: Expr[String])(using qctx: Quotes): Expr[TaskKey[A1]] =
    keyImpl[A1, TaskKey[A1]]("taskKey") { (name, mf) =>
      val n = Expr(name)
      '{
        TaskKey[A1]($n, $description)($mf)
      }
    }

  def inputKeyImpl[A1: Type](description: Expr[String])(using qctx: Quotes): Expr[InputKey[A1]] =
    keyImpl[A1, InputKey[A1]]("inputKey") { (name, mf) =>
      val n = Expr(name)
      '{
        InputKey[A1]($n, $description)($mf)
      }
    }

  private def keyImpl[A1: Type, A2: Type](methodName: String)(
      f: (String, Expr[ClassTag[A1]]) => Expr[A2]
  )(using qctx: Quotes): Expr[A2] =
    val tpe = summon[Type[A1]]
    f(
      definingValName(errorMsg(methodName)),
      Expr.summon[ClassTag[A1]].getOrElse(sys.error("ClassTag[A] not found for $tpe"))
    )

  private def keyImpl2[A1: Type, A2: Type](methodName: String)(
      f: (String, Expr[ClassTag[A1]], Expr[OptJsonWriter[A1]]) => Expr[A2]
  )(using qctx: Quotes): Expr[A2] =
    val tpe = summon[Type[A1]]
    f(
      definingValName(errorMsg(methodName)),
      Expr.summon[ClassTag[A1]].getOrElse(sys.error("ClassTag[A] not found for $tpe")),
      Expr.summon[OptJsonWriter[A1]].getOrElse(sys.error("OptJsonWriter[A] not found for $tpe")),
    )

  def projectImpl(using qctx: Quotes): Expr[Project] =
    val name = Expr(definingValName(errorMsg2("project")))
    '{
      Project($name, new File($name))
    }

  private def errorMsg(methodName: String): String =
    s"""$methodName must be directly assigned to a val, such as `val x = $methodName[Int]("description")`."""

  private def errorMsg2(methodName: String): String =
    s"""$methodName must be directly assigned to a val, such as `val x = ($methodName in file("core"))`."""

  private def definingValName(errorMsg: String)(using qctx: Quotes): String =
    val term = enclosingTerm
    if term.isValDef then term.name
    else sys.error(errorMsg)

  def enclosingTerm(using qctx: Quotes) =
    import qctx.reflect._
    def enclosingTerm0(sym: Symbol): Symbol =
      sym match
        case sym if sym.flags is Flags.Macro => enclosingTerm0(sym.owner)
        case sym if !sym.isTerm              => enclosingTerm0(sym.owner)
        case _                               => sym
    enclosingTerm0(Symbol.spliceOwner)
end KeyMacro
