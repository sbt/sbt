package p

import scala.language.experimental.macros
import scala.reflect.macros._

object Bar {
  def printTree(arg: Any): Any = macro BarMacros.printTreeImpl
}

class BarMacros(val c: blackbox.Context) {
  import c.universe._
  def printTreeImpl(arg: Tree): Tree = Literal(Constant("Bar2: " + arg))
}
