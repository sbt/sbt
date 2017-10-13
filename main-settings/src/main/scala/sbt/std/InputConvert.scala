/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package std

import reflect.macros._

import Def.Initialize
import sbt.internal.util.complete.Parser
import sbt.internal.util.appmacro.{ Convert, Converted }

object InputInitConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case InputWrapper.WrapInitName     => Converted.Success[c.type](in)
      case InputWrapper.WrapInitTaskName => Converted.Failure[c.type](in.pos, initTaskErrorMessage)
      case _                             => Converted.NotApplicable[c.type]
    }

  private def initTaskErrorMessage = "Internal sbt error: initialize+task wrapper not split"
}

/** Converts an input `Tree` of type `Parser[T]` or `State => Parser[T]` into a `Tree` of type `State => Parser[T]`.*/
object ParserConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case ParserInput.WrapName     => Converted.Success[c.type](in)
      case ParserInput.WrapInitName => Converted.Failure[c.type](in.pos, initParserErrorMessage)
      case _                        => Converted.NotApplicable[c.type]
    }

  private def initParserErrorMessage = "Internal sbt error: initialize+parser wrapper not split"
}

/** Convert instance for plain `Task`s not within the settings system. */
object TaskConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    if (nme == InputWrapper.WrapTaskName) Converted.Success[c.type](in)
    else Converted.NotApplicable[c.type]
}

/** Converts an input `Tree` of type `Initialize[T]`, `Initialize[Task[T]]`, or `Task[T]` into a `Tree` of type `Initialize[Task[T]]`.*/
object FullConvert extends Convert {
  import InputWrapper._
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case WrapInitTaskName => Converted.Success[c.type](in)
      case WrapPreviousName => Converted.Success[c.type](in)
      case WrapInitName     => wrapInit[T](c)(in)
      case WrapTaskName     => wrapTask[T](c)(in)
      case _                => Converted.NotApplicable[c.type]
    }

  private def wrapInit[T: c.WeakTypeTag](c: blackbox.Context)(tree: c.Tree): Converted[c.type] = {
    val i = c.Expr[Initialize[T]](tree)
    val t = c.universe.reify(Def.toITask(i.splice)).tree
    Converted.Success[c.type](t)
  }

  private def wrapTask[T: c.WeakTypeTag](c: blackbox.Context)(tree: c.Tree): Converted[c.type] = {
    val i = c.Expr[Task[T]](tree)
    val t = c.universe.reify(Def.valueStrict[Task[T]](i.splice)).tree
    Converted.Success[c.type](t)
  }
}

/**
 * Converts an input `Tree` of type `State => Parser[T]` or `Initialize[State => Parser[T]]`
 * into a `Tree` of type `Initialize[State => Parser[T]]`.
 */
object InitParserConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case ParserInput.WrapName     => wrap[T](c)(in)
      case ParserInput.WrapInitName => Converted.Success[c.type](in)
      case _                        => Converted.NotApplicable[c.type]
    }

  private def wrap[T: c.WeakTypeTag](c: blackbox.Context)(tree: c.Tree): Converted[c.type] = {
    val e = c.Expr[State => Parser[T]](tree)
    val t = c.universe.reify { Def.valueStrict[State => Parser[T]](e.splice) }
    Converted.Success[c.type](t.tree)
  }
}
