package sbt.internal

import sbt.internal.util.Types.Id
import sbt.internal.util.appmacro.*
import sbt.util.Applicative
import scala.quoted.*
import ConvertTestMacro.InputInitConvert

object ContTestMacro:
  inline def uncachedContMapNMacro[F[_]: Applicative, A](inline expr: A): List[A] =
    ${ uncachedContMapNMacroImpl[F, A]('expr) }

  def uncachedContMapNMacroImpl[F[_]: Type, A: Type](expr: Expr[A])(using
      qctx: Quotes
  ): Expr[List[A]] =
    object ContSyntax extends Cont
    import ContSyntax.*
    val convert1: Convert[qctx.type] = new InputInitConvert(qctx)
    convert1.contMapN[A, List, Id](
      tree = expr,
      applicativeExpr = convert1.summonAppExpr[List],
      None,
      None
    )
end ContTestMacro
