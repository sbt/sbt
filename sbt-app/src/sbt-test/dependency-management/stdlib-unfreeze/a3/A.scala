import scala.quoted.* // imports Quotes, Expr

package scala.collection.immutable {
  object Exp {
    // Access RedBlackTree.validate and Tree class added in Scala 2.13.13
    // Scala 3.3.4 uses Scala 2.13.14 library
    // Scala 3.3.2 uses Scala 2.13.12 library
    // Hence RedBlackTree.validate is available in 3.3.4 but not in 3.3.2
    // c.c. https://mvnrepository.com/artifact/org.scala-lang/scala3-library_3/3.3.2
    // c.c. https://mvnrepository.com/artifact/org.scala-lang/scala3-library_3/3.3.4
    def validateTree[A](tree: RedBlackTree.Tree[A, _])(implicit ordering: Ordering[A]): tree.type = {
      RedBlackTree.validate(tree)
    }
  }
}

object Mac:
  inline def inspect(inline x: Any): Any = ${ inspectCode('x) }

  def inspectCode(x: Expr[Any])(using Quotes): Expr[Any] =
    scala.collection.immutable.Exp.validateTree(null)(null)
    println(x.show)
    x

@main def huhu = println(scala.util.Properties.versionString)
