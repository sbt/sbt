package sbt.internal

import sbt.internal.util.appmacro.*
import scala.quoted.*

object ConvertTestMacro:
  final val WrapInitName = "wrapInit"
  final val WrapInitTaskName = "wrapInitTask"

  inline def someMacro(inline expr: Boolean): Boolean =
    ${ someMacroImpl('expr) }

  def someMacroImpl(expr: Expr[Boolean])(using qctx: Quotes) =
    val convert1: Convert[qctx.type] = new InputInitConvert(qctx)
    import convert1.qctx.reflect.*
    def addTypeCon(tpe: TypeRepr, qual: Term, selection: Term): Term =
      tpe.asType match
        case '[a] => '{ Option[a](${ selection.asExprOf[a] }) }.asTerm
    val substitute = (term: convert1.WrappedTerm) =>
      convert1.convert(term).transform(tree => addTypeCon(term.tpe, tree, term.oldTree))
    convert1.transformWrappers(expr.asTerm, substitute, Symbol.spliceOwner).asExprOf[Boolean]

  class InputInitConvert[C <: Quotes & scala.Singleton](override val qctx: C)
      extends Convert[C]
      with ContextUtil[C](0):
    // with TupleBuilder[C](qctx)
    // with TupleNBuilder[C](qctx):
    override def convert(in: WrappedTerm): Converted =
      in.name match
        case WrapInitName     => Converted.success(in.qual)
        case WrapInitTaskName => Converted.Failure(in.qual.pos, initTaskErrorMessage)
        case _                => Converted.NotApplicable()

    private def initTaskErrorMessage = "Internal sbt error: initialize+task wrapper not split"
  end InputInitConvert
end ConvertTestMacro
