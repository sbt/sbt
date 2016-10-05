package sbt
package std

import reflect.macros._

import Def.Initialize
import sbt.internal.util.complete.Parser
import sbt.internal.util.appmacro.{ Convert, Converted }

object InputInitConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case InputWrapper.WrapInitName     => Converted.Success(in)
      case InputWrapper.WrapInitTaskName => Converted.Failure(in.pos, initTaskErrorMessage)
      case _                             => Converted.NotApplicable
    }

  private def initTaskErrorMessage = "Internal sbt error: initialize+task wrapper not split"
}

/** Converts an input `Tree` of type `Parser[T]` or `State => Parser[T]` into a `Tree` of type `State => Parser[T]`.*/
object ParserConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case ParserInput.WrapName     => Converted.Success(in)
      case ParserInput.WrapInitName => Converted.Failure(in.pos, initParserErrorMessage)
      case _                        => Converted.NotApplicable
    }

  private def initParserErrorMessage = "Internal sbt error: initialize+parser wrapper not split"
}

/** Convert instance for plain `Task`s not within the settings system. */
object TaskConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    if (nme == InputWrapper.WrapTaskName) Converted.Success(in) else Converted.NotApplicable
}

/** Converts an input `Tree` of type `Initialize[T]`, `Initialize[Task[T]]`, or `Task[T]` into a `Tree` of type `Initialize[Task[T]]`.*/
object FullConvert extends Convert {
  import InputWrapper._
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case WrapInitTaskName => Converted.Success(in)
      case WrapPreviousName => Converted.Success(in)
      case WrapInitName     => wrapInit[T](c)(in)
      case WrapTaskName     => wrapTask[T](c)(in)
      case _                => Converted.NotApplicable
    }

  private def wrapInit[T](c: blackbox.Context)(tree: c.Tree): Converted[c.type] = {
    val i = c.Expr[Initialize[T]](tree)
    val t = c.universe.reify(Def.toITask(i.splice)).tree
    Converted.Success(t)
  }

  private def wrapTask[T](c: blackbox.Context)(tree: c.Tree): Converted[c.type] = {
    val i = c.Expr[Task[T]](tree)
    val t = c.universe.reify(Def.valueStrict[Task[T]](i.splice)).tree
    Converted.Success(t)
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
      case ParserInput.WrapInitName => Converted.Success(in)
      case _                        => Converted.NotApplicable
    }

  private def wrap[T](c: blackbox.Context)(tree: c.Tree): Converted[c.type] = {
    val e = c.Expr[State => Parser[T]](tree)
    val t = c.universe.reify { Def.valueStrict[State => Parser[T]](e.splice) }
    Converted.Success(t.tree)
  }
}
