/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package appmacro

import scala.reflect._
import macros._

/** A `TupleBuilder` that uses a KList as the tuple representation.*/
object KListBuilder extends TupleBuilder {
  def make(c: blackbox.Context)(mt: c.Type,
                                inputs: Inputs[c.universe.type]): BuilderResult[c.type] =
    new BuilderResult[c.type] {
      val ctx: c.type = c
      val util = ContextUtil[c.type](c)
      import c.universe.{ Apply => ApplyTree, _ }
      import util._

      val knilType = c.typeOf[KNil]
      val knil = Ident(knilType.typeSymbol.companion)
      val kconsTpe = c.typeOf[KCons[Int, KNil, List]]
      val kcons = kconsTpe.typeSymbol.companion
      val mTC: Type = mt.asInstanceOf[c.universe.Type]
      val kconsTC: Type = kconsTpe.typeConstructor

      /** This is the L in the type function [L[x]] ...  */
      val tcVariable: TypeSymbol = newTCVariable(util.initialOwner)

      /** Instantiates KCons[h, t <: KList[L], L], where L is the type constructor variable */
      def kconsType(h: Type, t: Type): Type =
        appliedType(kconsTC, h :: t :: refVar(tcVariable) :: Nil)

      def bindKList(prev: ValDef, revBindings: List[ValDef], params: List[ValDef]): List[ValDef] =
        params match {
          case (x @ ValDef(mods, name, tpt, _)) :: xs =>
            val rhs = select(Ident(prev.name), "head")
            val head = treeCopy.ValDef(x, mods, name, tpt, rhs)
            util.setSymbol(head, x.symbol)
            val tail = localValDef(TypeTree(), select(Ident(prev.name), "tail"))
            val base = head :: revBindings
            bindKList(tail, if (xs.isEmpty) base else tail :: base, xs)
          case Nil => revBindings.reverse
        }

      private[this] def makeKList(revInputs: Inputs[c.universe.type],
                                  klist: Tree,
                                  klistType: Type): Tree =
        revInputs match {
          case in :: tail =>
            val next = ApplyTree(
              TypeApply(Ident(kcons),
                        TypeTree(in.tpe) :: TypeTree(klistType) :: TypeTree(mTC) :: Nil),
              in.expr :: klist :: Nil)
            makeKList(tail, next, appliedType(kconsTC, in.tpe :: klistType :: mTC :: Nil))
          case Nil => klist
        }

      /** The input trees combined in a KList */
      val klist = makeKList(inputs.reverse, knil, knilType)

      /**
       * The input types combined in a KList type.  The main concern is tracking the heterogeneous types.
       * The type constructor is tcVariable, so that it can be applied to [X] X or M later.
       * When applied to `M`, this type gives the type of the `input` KList.
       */
      val klistType: Type = (inputs :\ knilType)((in, klist) => kconsType(in.tpe, klist))

      val representationC = internal.polyType(tcVariable :: Nil, klistType)
      val input = klist
      val alistInstance: ctx.universe.Tree =
        TypeApply(select(Ident(alist), "klist"), TypeTree(representationC) :: Nil)
      def extract(param: ValDef) = bindKList(param, Nil, inputs.map(_.local))
    }
}
