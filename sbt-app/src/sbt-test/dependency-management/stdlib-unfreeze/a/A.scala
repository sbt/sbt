import scala.language.reflectiveCalls

object A extends App {
  println(scala.util.Properties.versionString)
}

object AMacro {
  import scala.language.experimental.macros
  import scala.reflect.macros.blackbox.Context

  def m(x: Int): Int = macro impl

  def impl(c: Context)(x: c.Expr[Int]): c.Expr[Int] = {
    import c.universe._
    // added in 2.13.4
    val ec = (scala.concurrent.ExecutionContext: {def opportunistic: scala.concurrent.ExecutionContextExecutor}).opportunistic
    println(ec)
    c.Expr(q"2 + $x")
  }
}
