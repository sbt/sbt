import scala.quoted.* // imports Quotes, Expr

package scala.collection {
  object Exp:
    // added in 2.13.10, not available in 2.13.8
    def m(i: Int) = IterableOnce.checkArraySizeWithinVMLimit(i)
}

object Mac:
  inline def inspect(inline x: Any): Any = ${ inspectCode('x) }

  def inspectCode(x: Expr[Any])(using Quotes): Expr[Any] =
    scala.collection.Exp.m(42)
    println(x.show)
    x

@main def huhu = println(scala.util.Properties.versionString)
