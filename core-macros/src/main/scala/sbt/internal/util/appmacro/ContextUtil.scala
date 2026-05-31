/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package appmacro

import scala.jdk.CollectionConverters.*
import scala.quoted.*
import scala.collection.mutable
import sbt.util.cacheLevel
import sbt.util.CacheLevelTag
import xsbti.Attic

import scala.annotation.tailrec

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
        tupleTypeRepr(inputs.withFilter(_.isCacheInput).map(_.tpe))
      override def cacheInputExpr(tupleTerm: Term): Expr[Tuple] =
        Expr.ofTupleFromSeq(inputs.zipWithIndex.flatMap { (input, idx) =>
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
  private val transientSym = Symbol.requiredClass("scala.transient")
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
    @tailrec
    private def extractTags(tree: Term): List[CacheLevelTag] =
      def getCacheLevelAnnotation(tree: Term): Option[Term] =
        Option(tree.tpe.termSymbol) match
          case Some(x) => x.getAnnotation(cacheLevelSym)
          case None    => tree.symbol.getAnnotation(cacheLevelSym)
      def getTransientAnnotation(tree: Term): Option[Term] =
        Option(tree.tpe.termSymbol) match
          case Some(x) => x.getAnnotation(transientSym)
          case None    => tree.symbol.getAnnotation(transientSym)
      def extractTags0(tree: Term) =
        getCacheLevelAnnotation(tree) match
          case Some(annot) =>
            annot.asExprOf[cacheLevel] match
              case '{ cacheLevel(include = Array.empty[CacheLevelTag](using $_)) } => Nil
              case '{ cacheLevel(include = Array[CacheLevelTag]($include*)) }      =>
                include.value.get.toList
              case _ => sys.error(Printer.TreeStructure.show(annot) + " does not match")
          case _ =>
            getTransientAnnotation(tree) match
              case Some(annot) => Nil
              case _           => CacheLevelTag.all.toList
      tree match
        case Inlined(_, _, tree) => extractTags(tree)
        case Apply(_, List(arg)) => extractTags(arg)
        case _                   => extractTags0(tree)

  def cacheLevels(tree: Term): Seq[CacheLevelTag] =
    tree.underlying match
      // handles foo / bar cases
      case Apply(TypeApply(_, _), List(t @ Ident(_))) =>
        t.symbol.getAnnotation(cacheLevelSym) match
          case Some(_) => cacheLevelsForSym(t.symbol)
          case None    =>
            t.symbol.getAnnotation(transientSym) match
              case Some(_) => Nil
              case _       => CacheLevelTag.all.toList
      case u =>
        u.symbol.getAnnotation(cacheLevelSym) match
          case Some(_) => cacheLevelsForSym(u.symbol)
          case None    =>
            u.symbol.getAnnotation(transientSym) match
              case Some(_) => Nil
              case _       => CacheLevelTag.all.toList

  def cacheLevelsForSym(sym: Symbol): Seq[CacheLevelTag] =
    sym.getAnnotation(cacheLevelSym) match
      case Some(annot) if annot.symbol.owner.name == "cacheLevel" =>
        annot.asExprOf[cacheLevel] match
          case '{ cacheLevel(include = Array.empty[CacheLevelTag](using $_)) } => Nil
          case '{ cacheLevel(include = Array[CacheLevelTag]($include*)) }      =>
            include.value.get
          case _ => report.errorAndAbort(Printer.TreeStructure.show(annot) + " does not match")
      case _ => CacheLevelTag.all.toList

  enum OutputType:
    case File
    case Directory

  /**
   * Represents an output expression via:
   * 1. Def.declareOutput(VirtualFile)
   * 2. Def.declareOutputDirectory(VirtualFileRef)
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
    def toAssign(value: Term): Term =
      Block(
        Assign(toRef, value) :: Nil,
        toRef
      )
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

  private lazy val atticValues = Attic.getItems().asScala.toSet
  def hasVprintMacroSetting: Boolean =
    atticValues.contains("-Xmacro-settings:sbt:Vprint")
  def hasPrintTreeMacroSetting: Boolean =
    atticValues.contains("-Xmacro-settings:sbt:print-tree-structure")
end ContextUtil

object ContextUtil:
  def appendScalacOptions(options: Seq[String]): Unit =
    Attic.appendItems(options.asJava)

  def isTaskCacheByDefault: Boolean =
    val atticValues = Attic.getItems().asScala.toSet
    val noDefaultMacroSetting = atticValues.contains("-Xmacro-settings:sbt:no-default-task-cache")
    !noDefaultMacroSetting
end ContextUtil

class ContextUtil0[C <: Quotes & scala.Singleton](override val qctx: C, valStart: Int)
    extends ContextUtil[C](valStart):
// import qctx.reflect.*
end ContextUtil0
