package macros

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import java.io.File

object Provider {
  def tree(args: Any): File = macro treeImpl
  def treeImpl(c: blackbox.Context)(args: c.Expr[Any]): c.Expr[File] = {
    import c.universe._
    c.Expr(q"""new java.io.File(".")""")
  }
}
