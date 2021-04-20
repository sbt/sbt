package macros
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object Provider {
  def tree(args: Any): Any = macro treeImpl
  def treeImpl(c: blackbox.Context)(args: c.Expr[Any]) = sys.error("no macro for you!")
}
