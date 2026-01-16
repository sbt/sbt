package sbt.internal

import sbt.internal.util.Types.Id
import sbt.internal.util.appmacro.*
import sbt.util.Applicative
import scala.quoted.*
import ConvertTestMacro.InputInitConvert

object ContTestMacro:
  inline def uncachedContMapNMacro[F[_]: Applicative, A](inline expr: A): F[A] =
    ${ uncachedContMapNMacroImpl[F, A]('expr, '{ summon[Applicative[F]] }) }

  def uncachedContMapNMacroImpl[F[_]: Type, A: Type](expr: Expr[A], ev: Expr[Applicative[F]])(using
      qctx: Quotes
  ): Expr[F[A]] =
    object ContSyntax extends Cont
    import ContSyntax.*
    val convert1: Convert[qctx.type] = new InputInitConvert(qctx)
    convert1.contMapN[A, F, Id](
      tree = expr,
      applicativeExpr = ev,
      cacheConfigExpr = None,
    )
end ContTestMacro
