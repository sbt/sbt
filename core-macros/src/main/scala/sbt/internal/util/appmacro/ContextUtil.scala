/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package appmacro

import scala.compiletime.summonInline
import scala.quoted.*
import scala.collection.mutable
import sbt.util.cacheLevel
import sbt.util.CacheLevelTag

trait ContextUtil[C <: Quotes & scala.Singleton](val valStart: Int):
  val qctx: C
  import qctx.reflect.*
  given qctx.type = qctx

  private var counter: Int = valStart - 1
  def freshName(prefix: String): String =
    counter = counter + 1
    s"$$${prefix}${counter}"

  def freshValDef(parent: Symbol, tpe: TypeRepr, rhs: Term): ValDef =
    tpe.asType match
      case '[a] =>
        val sym =
          Symbol.newVal(
            parent,
            freshName("q"),
            tpe,
            Flags.Synthetic,
            Symbol.noSymbol
          )
        ValDef(sym, rhs = Some(rhs))

  def typed[A: Type](value: Term): Term =
    Typed(value, TypeTree.of[A])

  def makeTuple(inputs: List[Input]): BuilderResult =
    new BuilderResult:
      override lazy val inputTupleTypeRepr: TypeRepr =
        tupleTypeRepr(inputs.map(_.tpe))
      override def tupleExpr: Expr[Tuple] =
        Expr.ofTupleFromSeq(inputs.map(_.term.asExpr))
      override def cacheInputTupleTypeRepr: TypeRepr =
        tupleTypeRepr(inputs.filter(_.isCacheInput).map(_.tpe))
      override def cacheInputExpr(tupleTerm: Term): Expr[Tuple] =
        Expr.ofTupleFromSeq(inputs.zipWithIndex.flatMap { case (input, idx) =>
          if input.tags.nonEmpty then
            input.tpe.asType match
              case '[a] =>
                Some(applyTuple(tupleTerm, inputTupleTypeRepr, idx).asExprOf[a])
          else None
        })

  trait BuilderResult:
    def inputTupleTypeRepr: TypeRepr
    def tupleExpr: Expr[Tuple]
    def cacheInputTupleTypeRepr: TypeRepr
    def cacheInputExpr(tupleTerm: Term): Expr[Tuple]

  end BuilderResult

  def tupleTypeRepr(param: List[TypeRepr]): TypeRepr =
    param match
      case x :: xs => TypeRepr.of[scala.*:].appliedTo(List(x, tupleTypeRepr(xs)))
      case Nil     => TypeRepr.of[EmptyTuple]

  private val cacheLevelSym = Symbol.requiredClass("sbt.util.cacheLevel")
  final class Input(
      val tpe: TypeRepr,
      val qual: Term,
      val term: Term,
      val name: String,
  ):
    override def toString: String =
      s"Input($tpe, $qual, $term, $name, $tags)"
    def isCacheInput: Boolean = tags.nonEmpty
    lazy val tags = extractTags(qual)
    private def extractTags(tree: Term): List[CacheLevelTag] =
      def getAnnotation(tree: Term) =
        Option(tree.tpe.termSymbol) match
          case Some(x) => x.getAnnotation(cacheLevelSym)
          case None    => tree.symbol.getAnnotation(cacheLevelSym)
      def extractTags0(tree: Term) =
        getAnnotation(tree) match
          case Some(annot) =>
            annot.asExprOf[cacheLevel] match
              case '{ cacheLevel(include = Array.empty[CacheLevelTag]($_)) } => Nil
              case '{ cacheLevel(include = Array[CacheLevelTag]($include*)) } =>
                include.value.get.toList
              case _ => sys.error(Printer.TreeStructure.show(annot) + " does not match")
          case None => CacheLevelTag.all.toList
      tree match
        case Inlined(_, _, tree) => extractTags(tree)
        case Apply(_, List(arg)) => extractTags(arg)
        case _                   => extractTags0(tree)

  enum OutputType:
    case File
    case Directory

  /**
   * Represents an output expression via Def.declareOutput
   */
  final class Output(
      val tpe: TypeRepr,
      val term: Term,
      val name: String,
      val parent: Symbol,
      val outputType: OutputType,
  ):
    override def toString: String =
      s"Output($tpe, $term, $name, $outputType)"
    val placeholder: Symbol =
      tpe.asType match
        case '[a] =>
          Symbol.newVal(
            parent,
            name,
            tpe,
            Flags.Mutable,
            Symbol.noSymbol
          )
    def toVarDef: ValDef =
      ValDef(placeholder, rhs = Some('{ null }.asTerm))
    def toAssign: Term = Assign(toRef, term)
    def toRef: Ref = Ref(placeholder)
    def isFile: Boolean = outputType == OutputType.File
  end Output

  def applyTuple(tupleTerm: Term, tpe: TypeRepr, idx: Int): Term =
    Select
      .unique(Ref(tupleTerm.symbol), "apply")
      .appliedToTypes(List(tpe))
      .appliedToArgs(List(Literal(IntConstant(idx))))

  trait TermTransform[F[_]]:
    def apply(in: Term): Term
  end TermTransform

  def idTransform[F[_]]: TermTransform[F] = in => in

  def collectDefs(tree: Term, isWrapper: (String, TypeRepr, Term) => Boolean): Set[Symbol] =
    val defs = mutable.HashSet[Symbol]()
    object traverser extends TreeTraverser:
      override def traverseTree(tree: Tree)(owner: Symbol): Unit =
        tree match
          case Ident(_) => ()
          case Apply(TypeApply(Select(_, nme), tpe :: Nil), qual :: Nil)
              if isWrapper(nme, tpe.tpe, qual) =>
            ()
          case _ =>
            if tree.symbol ne null then defs += tree.symbol
            super.traverseTree(tree)(owner)
    end traverser
    traverser.traverseTree(tree)(Symbol.spliceOwner)
    defs.toSet
end ContextUtil
