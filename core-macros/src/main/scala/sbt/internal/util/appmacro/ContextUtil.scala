package sbt.internal.util.appmacro

import sbt.internal.util.Types.Id
import scala.compiletime.summonInline
import scala.quoted.*
import scala.reflect.TypeTest

trait ContextUtil[C <: Quotes & scala.Singleton](val qctx: C):
  import qctx.reflect.*
  given qctx.type = qctx

  /**
   * Returns a Type representing the type constructor tcp.<name>. For example, given `object Demo {
   * type M[x] = List[x] }`, the call `extractTypeCon(Demo, "M")` will return a type representing
   * the type constructor `[x] List[x]`.
   */
  def extractTypeCon(tcp: AnyRef & scala.Singleton, name: String)(using
      tcpt: Type[tcp.type]
  ): TypeRepr =
    val tcpTpe = TypeRepr.of[tcp.type]
    val fSym = tcpTpe.typeSymbol.declaredType(name).head
    val typeConTpe: TypeRepr = tcpTpe.memberType(fSym)
    val hiRepr = typeConTpe match
      case TypeBounds(low, TypeLambda(_, _, AppliedType(tc, _))) => tc
    hiRepr

  /**
   * Returns a reference given a singleton/termref
   */
  def extractSingleton[A: Type]: Expr[A] =
    def termRef(r: TypeRepr)(using rtt: TypeTest[TypeRepr, TermRef]): Ref = r match
      case rtt(ref) => Ref.term(ref)
      case _        => sys.error(s"expected termRef but got $r")
    termRef(TypeRepr.of[A]).asExprOf[A]

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

  def tupleTypeRepr(param: List[TypeRepr]): TypeRepr =
    param match
      case x :: xs => TypeRepr.of[scala.*:].appliedTo(List(x, tupleTypeRepr(xs)))
      case Nil     => TypeRepr.of[EmptyTuple]

  final class Input(
      val tpe: TypeRepr,
      val term: Term,
      val name: String
  )

  trait TermTransform[F[_]]:
    def apply(in: Term): Term
  end TermTransform

  def idTransform: TermTransform[Id] = in => in
end ContextUtil
