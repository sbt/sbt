import scala.reflect.macros.whitebox.Context
import scala.language.experimental.macros
import scala.annotation.StaticAnnotation

object HelloMacro {
  def impl(c: Context)(annottees: c.Tree*): c.Tree = {
    import c.universe._

    annottees match {
      case (classDecl: ClassDef) :: Nil =>
        val q"$mods class $name[..$tparams] $ctorMods(...$paramss) extends { ..$earlydefns } with ..$bases { $self => ..$body }" = classDecl
        q"""
        case class $name(...$paramss) extends ..$bases {
          ..$body
          def hello = "Hello"
        }
        """

      case _ => c.abort(c.enclosingPosition, "Invalid annottee")
    }
  }
}

class hello extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro HelloMacro.impl
}