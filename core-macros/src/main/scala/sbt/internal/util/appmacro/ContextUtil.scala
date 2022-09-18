package sbt.internal.util.appmacro

import sbt.internal.util.Types.Id
import scala.compiletime.summonInline
import scala.quoted.*
import scala.reflect.TypeTest
import scala.collection.mutable

trait ContextUtil[C <: Quotes & scala.Singleton](val qctx: C):
  import qctx.reflect.*
  given qctx.type = qctx

  private var counter: Int = -1
  def freshName(prefix: String): String =
    counter = counter + 1
    s"$$${prefix}${counter}"

  /**
   * Constructs a new, synthetic, local var with type `tpe`, a unique name, initialized to
   * zero-equivalent (Zero[A]), and owned by `parent`.
   */
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
      override def inputTupleTypeRepr: TypeRepr =
        tupleTypeRepr(inputs.map(_.tpe))
      override def tupleExpr: Expr[Tuple] =
        Expr.ofTupleFromSeq(inputs.map(_.term.asExpr))

  trait BuilderResult:
    def inputTupleTypeRepr: TypeRepr
    def tupleExpr: Expr[Tuple]
  end BuilderResult

  def tupleTypeRepr(param: List[TypeRepr]): TypeRepr =
    param match
      case x :: xs => TypeRepr.of[scala.*:].appliedTo(List(x, tupleTypeRepr(xs)))
      case Nil     => TypeRepr.of[EmptyTuple]

  final class Input(
      val tpe: TypeRepr,
      val qual: Term,
      val term: Term,
      val name: String
  ):
    override def toString: String =
      s"Input($tpe, $qual, $term, $name)"

  trait TermTransform[F[_]]:
    def apply(in: Term): Term
  end TermTransform

  def idTransform[F[_]]: TermTransform[F] = in => in

  /**
   * {
   *  val in$proxy264: sbt.Def.Initialize[sbt.Task[sbt.ForkOptions]] = sbt.Defaults.forkOptionsTask
   *  (foo.bar[sbt.ForkOptions](in$proxy264): sbt.ForkOptions)
   * }
   *
   * @param tree
   * @return
   */
  def inlineExtensionProxy(tree: Term): Term =
    val rhss: mutable.Map[String, Term] = mutable.Map.empty
    object refTransformer extends TreeMap:
      override def transformStatement(tree: Statement)(owner: Symbol): Statement =
        tree match
          case ValDef(name, tpe, Some(body)) if name.contains("$proxy") =>
            rhss(name) = body
            ValDef.copy(tree)(name, tpe, None)
          case _ =>
            super.transformStatement(tree)(owner)

      override def transformTerm(tree: Term)(owner: Symbol): Term =
        tree match
          case Ident(name) if rhss.contains(name) => rhss(name)
          case _                                  => super.transformTerm(tree)(owner)
    end refTransformer
    refTransformer.transformTerm(tree)(Symbol.spliceOwner)

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
