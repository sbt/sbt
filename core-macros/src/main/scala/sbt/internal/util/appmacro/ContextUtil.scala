package sbt.internal.util.appmacro

import sbt.internal.util.Types.Id
import scala.compiletime.summonInline
import scala.quoted.*
import scala.reflect.TypeTest
import scala.collection.mutable

trait ContextUtil[C <: Quotes & scala.Singleton](val qctx: C, val valStart: Int):
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
          if input.isCacheInput then
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

  private val cacheOptOutSym = Symbol.requiredClass("sbt.util.cacheOptOut")
  final class Input(
      val tpe: TypeRepr,
      val qual: Term,
      val term: Term,
      val name: String,
  ):
    override def toString: String =
      s"Input($tpe, $qual, $term, $name)"

    def isCacheInput: Boolean = isCacheInput(qual)
    private def isCacheInput(tree: Term): Boolean =
      tree match
        case Inlined(_, _, tree) => isCacheInput(tree)
        case Apply(_, List(arg)) => isCacheInput(arg)
        case _ =>
          Option(tree.tpe.termSymbol) match
            case Some(x) => !x.hasAnnotation(cacheOptOutSym)
            case None    => !tree.symbol.hasAnnotation(cacheOptOutSym)

  /**
   * Represents an output expression via Def.declareOutput
   */
  final class Output(
      val tpe: TypeRepr,
      val term: Term,
      val name: String,
      val parent: Symbol,
  ):
    override def toString: String =
      s"Output($tpe, $term, $name)"
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
