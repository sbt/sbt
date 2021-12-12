/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.appmacro

import sbt.internal.util.Types
import scala.quoted.*

/**
 * Convert is a glorified partial function to scan through the AST for the purpose of substituting
 * the matching term with something else.
 *
 * This is driven by calling transformWrappers(...) method. The filtering is limited to the shape of
 * code matched using `appTransformer`, which is a generic function with a single type param and a
 * single term param like `X.wrapInit[A](...)`.
 */
trait Convert[C <: Quotes & Singleton](override val qctx: C) extends ContextUtil[C]:
  // with TupleBuilder[C]:
  import qctx.reflect.*

  def convert[A: Type](nme: String, in: Term): Converted

  def asPredicate[A]: (String, Type[A], Term) => Boolean =
    (n, tpe, tree) =>
      val tag = tpe
      convert(n, tree)(tag).isSuccess

  /**
   * Substitutes wrappers in tree `t` with the result of `subWrapper`. A wrapper is a Tree of the
   * form `f[T](v)` for which isWrapper(<Tree of f>, <Underlying Type>, <qual>.target) returns true.
   * Typically, `f` is a `Select` or `Ident`. The wrapper is replaced with the result of
   * `subWrapper(<Type of T>, <Tree of v>, <wrapper Tree>)`
   */
  def transformWrappers(
      tree: Term,
      subWrapper: (String, TypeRepr, Term, Term) => Converted,
      owner: Symbol,
  ): Term =
    // the main tree transformer that replaces calls to InputWrapper.wrap(x) with
    //  plain Idents that reference the actual input value
    object appTransformer extends TreeMap:
      override def transformTerm(tree: Term)(owner: Symbol): Term =
        tree match
          case Apply(TypeApply(Select(_, nme), targ :: Nil), qual :: Nil) =>
            subWrapper(nme, targ.tpe, qual, tree) match
              case Converted.Success(tree, finalTransform) =>
                finalTransform(tree)
              case Converted.Failure(position, message) =>
                report.error(message, position)
                sys.error("macro error: " + message)
              case _ =>
                super.transformTerm(tree)(owner)
          case _ =>
            super.transformTerm(tree)(owner)
    end appTransformer
    appTransformer.transformTerm(tree)(owner)

  object Converted:
    def success(tree: Term) = Converted.Success(tree, Types.idFun)

  enum Converted:
    def isSuccess: Boolean = this match
      case Success(_, _) => true
      case _             => false

    def transform(f: Term => Term): Converted = this match
      case Success(tree, finalTransform) => Success(f(tree), finalTransform)
      case x: Failure                    => x
      case x: NotApplicable              => x

    case Success(tree: Term, finalTransform: Term => Term) extends Converted
    case Failure(position: Position, message: String) extends Converted
    case NotApplicable() extends Converted
  end Converted
end Convert
