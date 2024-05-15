/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import Def.Initialize
import sbt.internal.util.Types.Id
import sbt.internal.util.appmacro.{
  Cont,
  ContextUtil,
  Convert,
  // LinterDSL,
}
import sbt.util.Applicative
import scala.quoted.*
import sbt.internal.util.complete.Parser

class InitializeConvert[C <: Quotes & scala.Singleton](override val qctx: C, valStart: Int)
    extends Convert[C]
    with ContextUtil[C](valStart):
  override def convert(in: WrappedTerm): Converted =
    in.name match
      case InputWrapper.WrapInitName => Converted.success(in.qual)
      case InputWrapper.WrapTaskName | InputWrapper.WrapInitTaskName =>
        Converted.Failure(in.qual.pos, "A setting cannot depend on a task")
      case InputWrapper.WrapPreviousName =>
        Converted.Failure(in.qual.pos, "A setting cannot depend on a task's previous value.")
      case _ => Converted.NotApplicable()

  def appExpr: Expr[Applicative[Initialize]] =
    '{ InitializeInstance.initializeMonad }
end InitializeConvert

object SettingMacro:
  // import LinterDSL.{ Empty => EmptyLinter }

  type F[x] = Initialize[x]
  object ContSyntax extends Cont
  import ContSyntax.*

  def settingMacroImpl[A1: Type](in: Expr[A1])(using qctx: Quotes): Expr[Initialize[A1]] =
    val convert1 = InitializeConvert(qctx, 0)
    convert1.contMapN[A1, F, Id](in, convert1.appExpr, None, None)

  def settingDynImpl[A1: Type](in: Expr[Initialize[A1]])(using qctx: Quotes): Expr[Initialize[A1]] =
    val convert1 = InitializeConvert(qctx, 0)
    convert1.contFlatMap[A1, F, Id](in, convert1.appExpr, None)

  def inputMacroImpl[A1: Type](in: Expr[State => Parser[A1]])(using
      qctx: Quotes
  ): Expr[ParserGen[A1]] =
    val convert1 = InitializeConvert(qctx, 0)
    val init1 = convert1.contMapN[State => Parser[A1], F, Id](in, convert1.appExpr, None, None)
    '{ ParserGen[A1]($init1) }
end SettingMacro
