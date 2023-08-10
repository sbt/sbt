package sbt.internal

import sbt.internal.util.Types.Id
import sbt.internal.util.appmacro.*
import sbt.util.Applicative
import sbt.util.{ ActionCacheStore, InMemoryActionCacheStore }
import scala.quoted.*
import ConvertTestMacro.InputInitConvert

object ContTestMacro:
  inline def contMapNMacro[F[_]: Applicative, A](inline expr: A): List[A] =
    ${ contMapNMacroImpl[F, A]('expr) }

  def contMapNMacroImpl[F[_]: Type, A: Type](expr: Expr[A])(using
      qctx: Quotes
  ): Expr[List[A]] =
    object ContSyntax extends Cont
    import ContSyntax.*
    val convert1: Convert[qctx.type] = new InputInitConvert(qctx)
    convert1.contMapN[A, List, Id](
      expr,
      convert1.summonAppExpr[List],
      '{ Seq(inMemoryCache: ActionCacheStore) },
      convert1.idTransform
    )

  lazy val inMemoryCache = InMemoryActionCacheStore()
end ContTestMacro
