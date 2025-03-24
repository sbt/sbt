import scala.language.reflectiveCalls


package scala.collection.immutable {
  object Exp {
    // Access RedBlackTree.validate added in Scala 2.13.13
    def v = RedBlackTree.validate(null)(null)
  }
}


object A extends App {
  println(scala.util.Properties.versionString)
}

object AMacro {
  import scala.language.experimental.macros
  import scala.reflect.macros.blackbox.Context

  def m(x: Int): Int = macro impl

  def impl(c: Context)(x: c.Expr[Int]): c.Expr[Int] = {
    import c.universe._
    println(scala.collection.immutable.Exp.v)
    c.Expr(q"2 + $x")
  }
}
