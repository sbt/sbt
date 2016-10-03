package p

import scala.language.experimental.macros
import scala.reflect.macros._

object Baz {
  def printTree(arg: Any): Any = macro BazMacros.printTreeImpl
}

class BazMacros(val c: blackbox.Context) {
  import c.universe._
  def printTreeImpl(arg: Tree): Tree = Literal(Constant("Baz1: " + arg))
}
