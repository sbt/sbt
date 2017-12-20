/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package std

import language.experimental.macros
import reflect.macros._
import reflect.internal.annotations.compileTimeOnly

import Def.Initialize
import sbt.internal.util.appmacro.ContextUtil
import sbt.internal.util.complete.Parser

/** Implementation detail.  The wrap methods temporarily hold inputs (as a Tree, at compile time) until a task or setting macro processes it. */
object InputWrapper {
  /* The names of the wrapper methods should be obscure.
	* Wrapper checking is based solely on this name, so it must not conflict with a user method name.
	* The user should never see this method because it is compile-time only and only used internally by the task macro system.*/

  private[std] final val WrapTaskName = "wrapTask_\u2603\u2603"
  private[std] final val WrapInitName = "wrapInit_\u2603\u2603"
  private[std] final val WrapInitTaskName = "wrapInitTask_\u2603\u2603"
  private[std] final val WrapInitInputName = "wrapInitInputTask_\u2603\u2603"
  private[std] final val WrapInputName = "wrapInputTask_\u2603\u2603"
  private[std] final val WrapPreviousName = "wrapPrevious_\u2603\u2603"

  @compileTimeOnly(
    "`value` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task.")
  def wrapTask_\u2603\u2603[T](in: Any): T = implDetailError

  @compileTimeOnly(
    "`value` can only be used within a task or setting macro, such as :=, +=, ++=, Def.task, or Def.setting.")
  def wrapInit_\u2603\u2603[T](in: Any): T = implDetailError

  @compileTimeOnly(
    "`value` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task.")
  def wrapInitTask_\u2603\u2603[T](in: Any): T = implDetailError

  @compileTimeOnly(
    "`value` can only be called on an input task within a task definition macro, such as := or Def.inputTask.")
  def wrapInputTask_\u2603\u2603[T](in: Any): T = implDetailError

  @compileTimeOnly(
    "`value` can only be called on an input task within a task definition macro, such as := or Def.inputTask.")
  def wrapInitInputTask_\u2603\u2603[T](in: Any): T = implDetailError

  @compileTimeOnly(
    "`previous` can only be called on a task within a task or input task definition macro, such as :=, +=, ++=, Def.task, or Def.inputTask.")
  def wrapPrevious_\u2603\u2603[T](in: Any): T = implDetailError

  private[this] def implDetailError =
    sys.error("This method is an implementation detail and should not be referenced.")

  private[std] def wrapTask[T: c.WeakTypeTag](c: blackbox.Context)(
      ts: c.Expr[Any],
      pos: c.Position
  ): c.Expr[T] =
    wrapImpl[T, InputWrapper.type](c, InputWrapper, WrapTaskName)(ts, pos)

  private[std] def wrapInit[T: c.WeakTypeTag](c: blackbox.Context)(
      ts: c.Expr[Any],
      pos: c.Position
  ): c.Expr[T] =
    wrapImpl[T, InputWrapper.type](c, InputWrapper, WrapInitName)(ts, pos)

  private[std] def wrapInitTask[T: c.WeakTypeTag](c: blackbox.Context)(
      ts: c.Expr[Any],
      pos: c.Position
  ): c.Expr[T] =
    wrapImpl[T, InputWrapper.type](c, InputWrapper, WrapInitTaskName)(ts, pos)

  private[std] def wrapInitInputTask[T: c.WeakTypeTag](c: blackbox.Context)(
      ts: c.Expr[Any],
      pos: c.Position
  ): c.Expr[T] =
    wrapImpl[T, InputWrapper.type](c, InputWrapper, WrapInitInputName)(ts, pos)

  private[std] def wrapInputTask[T: c.WeakTypeTag](c: blackbox.Context)(
      ts: c.Expr[Any],
      pos: c.Position
  ): c.Expr[T] =
    wrapImpl[T, InputWrapper.type](c, InputWrapper, WrapInputName)(ts, pos)

  private[std] def wrapPrevious[T: c.WeakTypeTag](c: blackbox.Context)(
      ts: c.Expr[Any],
      pos: c.Position
  ): c.Expr[Option[T]] =
    wrapImpl[Option[T], InputWrapper.type](c, InputWrapper, WrapPreviousName)(ts, pos)

  /**
   * Wraps an arbitrary Tree in a call to the `<s>.<wrapName>` method of this module for later processing by an enclosing macro.
   * The resulting Tree is the manually constructed version of:
   *
   * `c.universe.reify { <s>.<wrapName>[T](ts.splice) }`
   */
  def wrapImpl[T: c.WeakTypeTag, S <: AnyRef with Singleton](
      c: blackbox.Context,
      s: S,
      wrapName: String
  )(ts: c.Expr[Any], pos: c.Position)(implicit it: c.TypeTag[s.type]): c.Expr[T] = {
    import c.universe.{ Apply => ApplyTree, _ }
    import internal.decorators._
    val util = new ContextUtil[c.type](c)
    val iw = util.singleton(s)
    val tpe = c.weakTypeOf[T]
    val nme = TermName(wrapName).encodedName
    val sel = Select(Ident(iw), nme)
    sel.setPos(pos) // need to set the position on Select, because that is where the compileTimeOnly check looks
    val tree = ApplyTree(TypeApply(sel, TypeTree(tpe) :: Nil), ts.tree :: Nil)
    tree.setPos(ts.tree.pos)
    // JZ: I'm not sure why we need to do this. Presumably a caller is wrapping this tree in a
    //     typed tree *before* handing the whole thing back to the macro engine. One must never splice
    //     untyped trees under typed trees, as the type checker doesn't descend if `tree.tpe == null`.
    //
    //     #1031 The previous attempt to fix this just set the type on `tree`, which worked in cases when the
    //     call to `.value` was inside a the task macro and eliminated before the end of the typer phase.
    //     But, if a "naked" call to `.value` left the typer, the superaccessors phase would freak out when
    //     if hit the untyped trees, before we could get to refchecks and the desired @compileTimeOnly warning.
    val typedTree = c.typecheck(tree)
    c.Expr[T](typedTree)
  }

  def valueMacroImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] =
    ContextUtil.selectMacroImpl[T](c) { (ts, pos) =>
      ts.tree.tpe match {
        case tpe if tpe <:< c.weakTypeOf[Initialize[T]] =>
          if (c.weakTypeOf[T] <:< c.weakTypeOf[InputTask[_]]) {
            c.abort(
              pos,
              """`value` is removed from input tasks. Use `evaluated` or `inputTaskValue`.
                           |See http://www.scala-sbt.org/1.0/docs/Input-Tasks.html for more details.""".stripMargin
            )
          }
          InputWrapper.wrapInit[T](c)(ts, pos)
        case tpe if tpe <:< c.weakTypeOf[Initialize[Task[T]]] =>
          InputWrapper.wrapInitTask[T](c)(ts, pos)
        case tpe if tpe <:< c.weakTypeOf[Task[T]]      => InputWrapper.wrapTask[T](c)(ts, pos)
        case tpe if tpe <:< c.weakTypeOf[InputTask[T]] => InputWrapper.wrapInputTask[T](c)(ts, pos)
        case tpe if tpe <:< c.weakTypeOf[Initialize[InputTask[T]]] =>
          InputWrapper.wrapInitInputTask[T](c)(ts, pos)
        case tpe => unexpectedType(c)(pos, tpe)
      }
    }
  def inputTaskValueMacroImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[InputTask[T]] =
    ContextUtil.selectMacroImpl[InputTask[T]](c) { (ts, pos) =>
      InputWrapper.wrapInit[InputTask[T]](c)(ts, pos)
    }
  def taskValueMacroImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Task[T]] =
    ContextUtil.selectMacroImpl[Task[T]](c) { (ts, pos) =>
      val tpe = ts.tree.tpe
      if (tpe <:< c.weakTypeOf[Initialize[Task[T]]])
        InputWrapper.wrapInit[Task[T]](c)(ts, pos)
      else
        unexpectedType(c)(pos, tpe)
    }

  /** Translates <task: TaskKey[T]>.previous(format) to Previous.runtime(<task>)(format).value*/
  def previousMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      format: c.Expr[sjsonnew.JsonFormat[T]]): c.Expr[Option[T]] = {
    import c.universe._
    c.macroApplication match {
      case a @ Apply(Select(Apply(_, t :: Nil), tp), fmt) =>
        if (t.tpe <:< c.weakTypeOf[TaskKey[T]]) {
          val tsTyped = c.Expr[TaskKey[T]](t)
          val newTree = c.universe.reify { Previous.runtime[T](tsTyped.splice)(format.splice) }
          wrapPrevious[T](c)(newTree, a.pos)
        } else
          unexpectedType(c)(a.pos, t.tpe)
      case x => ContextUtil.unexpectedTree(x)
    }
  }

  private def unexpectedType(c: blackbox.Context)(pos: c.Position, tpe: c.Type) =
    c.abort(pos, s"Internal sbt error. Unexpected type ${tpe.widen}")
}

sealed abstract class MacroTaskValue[T] {
  @compileTimeOnly(
    "`taskValue` can only be used within a setting macro, such as :=, +=, ++=, or Def.setting.")
  def taskValue: Task[T] = macro InputWrapper.taskValueMacroImpl[T]
}
sealed abstract class MacroValue[T] {
  @compileTimeOnly(
    "`value` can only be used within a task or setting macro, such as :=, +=, ++=, Def.task, or Def.setting.")
  def value: T = macro InputWrapper.valueMacroImpl[T]
}
sealed abstract class ParserInput[T] {
  @compileTimeOnly(
    "`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
  def parsed: T = macro ParserInput.parsedMacroImpl[T]
}
sealed abstract class InputEvaluated[T] {
  @compileTimeOnly(
    "`evaluated` can only be used within an input task macro, such as := or Def.inputTask.")
  def evaluated: T = macro InputWrapper.valueMacroImpl[T]
  @compileTimeOnly(
    "`inputTaskValue` can only be used within an input task macro, such as := or Def.inputTask.")
  def inputTaskValue: InputTask[T] = macro InputWrapper.inputTaskValueMacroImpl[T]
}
sealed abstract class ParserInputTask[T] {
  @compileTimeOnly(
    "`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
  def parsed: Task[T] = macro ParserInput.parsedInputMacroImpl[T]
}
sealed abstract class MacroPrevious[T] {
  @compileTimeOnly(
    "`previous` can only be used within a task macro, such as :=, +=, ++=, or Def.task.")
  def previous(implicit format: sjsonnew.JsonFormat[T]): Option[T] =
    macro InputWrapper.previousMacroImpl[T]
}

/** Implementation detail.  The wrap method temporarily holds the input parser (as a Tree, at compile time) until the input task macro processes it. */
object ParserInput {
  /* The name of the wrapper method should be obscure.
	* Wrapper checking is based solely on this name, so it must not conflict with a user method name.
	* The user should never see this method because it is compile-time only and only used internally by the task macros.*/
  private[std] val WrapName = "parser_\u2603\u2603"
  private[std] val WrapInitName = "initParser_\u2603\u2603"

  @compileTimeOnly(
    "`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
  def parser_\u2603\u2603[T](i: Any): T =
    sys.error("This method is an implementation detail and should not be referenced.")

  @compileTimeOnly(
    "`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
  def initParser_\u2603\u2603[T](i: Any): T =
    sys.error("This method is an implementation detail and should not be referenced.")

  private[std] def wrap[T: c.WeakTypeTag](c: blackbox.Context)(ts: c.Expr[Any],
                                                               pos: c.Position): c.Expr[T] =
    InputWrapper.wrapImpl[T, ParserInput.type](c, ParserInput, WrapName)(ts, pos)
  private[std] def wrapInit[T: c.WeakTypeTag](c: blackbox.Context)(ts: c.Expr[Any],
                                                                   pos: c.Position): c.Expr[T] =
    InputWrapper.wrapImpl[T, ParserInput.type](c, ParserInput, WrapInitName)(ts, pos)

  private[std] def inputParser[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[InputTask[T]]): c.Expr[State => Parser[Task[T]]] =
    c.universe.reify(t.splice.parser)

  def parsedInputMacroImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Task[T]] =
    ContextUtil.selectMacroImpl[Task[T]](c) { (p, pos) =>
      p.tree.tpe match {
        case tpe if tpe <:< c.weakTypeOf[InputTask[T]] => wrapInputTask[T](c)(p.tree, pos)
        case tpe if tpe <:< c.weakTypeOf[Initialize[InputTask[T]]] =>
          wrapInitInputTask[T](c)(p.tree, pos)
        case tpe => unexpectedType(c)(pos, tpe, "parsedInputMacroImpl")
      }
    }

  private def wrapInputTask[T: c.WeakTypeTag](
      c: blackbox.Context
  )(tree: c.Tree, pos: c.Position) = {
    val e = c.Expr[InputTask[T]](tree)
    wrap[Task[T]](c)(inputParser(c)(e), pos)
  }

  private def wrapInitInputTask[T: c.WeakTypeTag](c: blackbox.Context)(tree: c.Tree,
                                                                       pos: c.Position) = {
    val e = c.Expr[Initialize[InputTask[T]]](tree)
    wrapInit[Task[T]](c)(c.universe.reify { Def.toIParser(e.splice) }, pos)
  }

  /** Implements `Parser[T].parsed` by wrapping the Parser with the ParserInput wrapper.*/
  def parsedMacroImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] =
    ContextUtil.selectMacroImpl[T](c) { (p, pos) =>
      p.tree.tpe match {
        case tpe if tpe <:< c.weakTypeOf[Parser[T]]          => wrapParser[T](c)(p.tree, pos)
        case tpe if tpe <:< c.weakTypeOf[State => Parser[T]] => wrap[T](c)(p, pos)
        case tpe if tpe <:< c.weakTypeOf[Initialize[Parser[T]]] =>
          wrapInitParser[T](c)(p.tree, pos)
        case tpe if tpe <:< c.weakTypeOf[Initialize[State => Parser[T]]] => wrapInit[T](c)(p, pos)
        case tpe                                                         => unexpectedType(c)(pos, tpe, "parsedMacroImpl")
      }
    }

  private def wrapParser[T: c.WeakTypeTag](c: blackbox.Context)(tree: c.Tree, pos: c.Position) = {
    val e = c.Expr[Parser[T]](tree)
    wrap[T](c)(c.universe.reify { Def.toSParser(e.splice) }, pos)
  }

  private def wrapInitParser[T: c.WeakTypeTag](
      c: blackbox.Context
  )(tree: c.Tree, pos: c.Position) = {
    val e = c.Expr[Initialize[Parser[T]]](tree)
    val es = c.universe.reify { Def.toISParser(e.splice) }
    wrapInit[T](c)(es, pos)
  }

  private def unexpectedType(c: blackbox.Context)(pos: c.Position, tpe: c.Type, label: String) =
    c.abort(pos, s"Internal sbt error. Unexpected type ${tpe.dealias} in $label.")
}
