/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import sbt.internal.util.appmacro.{ Convert, ContextUtil }
import sbt.internal.util.complete.Parser
import Def.Initialize
import sbt.util.Applicative
import sbt.internal.util.Types.Compose
import scala.quoted.*

class InputInitConvert[C <: Quotes & scala.Singleton](override val qctx: C, valStart: Int)
    extends Convert[C](qctx)
    with ContextUtil[C](qctx, valStart):
  import qctx.reflect.*

  override def convert[A: Type](nme: String, in: Term): Converted =
    nme match
      case InputWrapper.WrapInitName     => Converted.success(in)
      case InputWrapper.WrapInitTaskName => Converted.Failure(in.pos, initTaskErrorMessage)
      case _                             => Converted.NotApplicable()

  private def initTaskErrorMessage = "Internal sbt error: initialize+task wrapper not split"

  def appExpr: Expr[Applicative[Initialize]] =
    '{ InitializeInstance.initializeMonad }
end InputInitConvert

/** Converts an input `Term` of type `Parser[A]` or `State => Parser[A]` into a `Term` of type `State => Parser[A]`. */
class ParserConvert[C <: Quotes & scala.Singleton](override val qctx: C, valStart: Int)
    extends Convert[C](qctx)
    with ContextUtil[C](qctx, valStart):
  import qctx.reflect.*

  override def convert[A: Type](nme: String, in: Term): Converted =
    nme match
      case ParserInput.WrapName     => Converted.success(in)
      case ParserInput.WrapInitName => Converted.Failure(in.pos, initParserErrorMessage)
      case _                        => Converted.NotApplicable()

  private def initParserErrorMessage = "Internal sbt error: initialize+parser wrapper not split"

  def appExpr: Expr[Applicative[ParserInstance.F1]] =
    '{ ParserInstance.parserFunApplicative }
end ParserConvert

/** Convert instance for plain `Task`s not within the settings system. */
class TaskConvert[C <: Quotes & scala.Singleton](override val qctx: C, valStart: Int)
    extends Convert[C](qctx)
    with ContextUtil[C](qctx, valStart):
  import qctx.reflect.*
  override def convert[A: Type](nme: String, in: Term): Converted =
    if nme == InputWrapper.WrapTaskName then Converted.success(in)
    else Converted.NotApplicable()

  def appExpr[Expr[Monad[Task]]] =
    '{ Task.taskMonad }
end TaskConvert

/**
 * Converts an input `Term` of type `Initialize[A]`, `Initialize[Task[A]]`, or `Task[A]` into
 * a `Term` of type `Initialize[Task[A]]`.
 */
class FullConvert[C <: Quotes & scala.Singleton](override val qctx: C, valStart: Int)
    extends Convert[C](qctx)
    with ContextUtil[C](qctx, valStart):
  import qctx.reflect.*

  override def convert[A: Type](nme: String, in: Term): Converted =
    nme match
      case InputWrapper.WrapInitTaskName => Converted.success(in)
      case InputWrapper.WrapPreviousName => Converted.success(in)
      case InputWrapper.WrapInitName     => wrapInit[A](in)
      case InputWrapper.WrapTaskName     => wrapTask[A](in)
      case InputWrapper.WrapOutputName   => Converted.success(in)
      case _                             => Converted.NotApplicable()

  private def wrapInit[A: Type](tree: Term): Converted =
    val expr = tree.asExprOf[Initialize[A]]
    val t = '{
      Def.toITask[A]($expr)
    }
    Converted.success(t.asTerm)

  private def wrapTask[A: Type](tree: Term): Converted =
    val i = tree.asExprOf[Task[A]]
    val t = '{
      Def.valueStrict[Task[A]]($i)
    }
    Converted.success(t.asTerm)

  def appExpr: Expr[Applicative[Compose[Initialize, Task]]] =
    '{ FullInstance.initializeTaskMonad }
end FullConvert

/**
 * Converts an input `Term` of type `State => Parser[A]` or `Initialize[State => Parser[A]]`
 * into a `Term` of type `Initialize[State => Parser[A]]`.
 */
class InitParserConvert[C <: Quotes & scala.Singleton](override val qctx: C, valStart: Int)
    extends Convert[C](qctx)
    with ContextUtil[C](qctx, valStart):
  import qctx.reflect.*

  override def convert[A: Type](nme: String, in: Term): Converted =
    nme match
      case ParserInput.WrapName     => wrap[A](in)
      case ParserInput.WrapInitName => Converted.success(in)
      case _                        => Converted.NotApplicable()

  private def wrap[A: Type](tree: Term): Converted =
    val e = tree.asExprOf[State => Parser[A]]
    val t = '{
      Def.valueStrict[State => Parser[A]]($e)
    }
    Converted.success(t.asTerm)

end InitParserConvert
