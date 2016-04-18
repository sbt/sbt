package example

import scala.language.experimental.macros
import scala.reflect.macros._

object Provider {
  def tree(args: Any): Any = macro treeImpl
  def treeImpl(c: Context)(args: c.Expr[Any]) = {
    import c.universe._
    c.Expr[Any](
      Apply(
        Select(Ident(TermName("Bar")), TermName("bar")),
        List(Literal(Constant(0))))
    )
  }
}
