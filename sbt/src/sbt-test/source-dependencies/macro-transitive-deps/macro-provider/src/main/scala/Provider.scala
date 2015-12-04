package macros
import scala.language.experimental.macros
import scala.reflect.macros._
import scala.tools.reflect.Eval

object Provider {
  def dummyMacro: Int = macro dummyMacroImpl
  def dummyMacroImpl(c: Context): c.Expr[Int] = {
    import c.universe._
    val result = c.eval(reify(Relay4.relay4))
    c.Expr(Literal(Constant(result)))
  }
}