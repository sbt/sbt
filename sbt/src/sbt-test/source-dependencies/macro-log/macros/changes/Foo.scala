package p

import scala.language.experimental.macros
import scala.reflect.macros._

object Foo {
  def printTree(arg: Any): Any = macro FooMacros.printTreeImpl
}

class FooMacros(val c: blackbox.Context) {
  import c.universe._
  def printTreeImpl(arg: Tree): Tree = Literal(Constant("Foo2: " + arg))
}
