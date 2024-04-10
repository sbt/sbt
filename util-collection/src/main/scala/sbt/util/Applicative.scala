/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

trait Applicative[F[_]] extends Apply[F]:
  def pure[A1](x: () => A1): F[A1]

  override def map[A1, A2](fa: F[A1])(f: A1 => A2): F[A2] =
    ap(pure(() => f))(fa)
end Applicative

object Applicative:
  given Applicative[Option] = OptionInstances.optionMonad
  given Applicative[List] = ListInstances.listMonad

  given [F1[_], F2[_]](using Applicative[F1], Applicative[F2]): Applicative[[a] =>> F1[F2[a]]] with
    type F[x] = F1[F2[x]]
    val F1 = summon[Applicative[F1]]
    val F2 = summon[Applicative[F2]]
    override def pure[A1](x: () => A1): F1[F2[A1]] = F1.pure(() => F2.pure(x))
    override def map[A1, A2](fa: F1[F2[A1]])(f: A1 => A2): F1[F2[A2]] =
      F1.map(fa)(f2 => F2.map(f2)(f))
    override def ap[A1, A2](f1f2f: F1[F2[A1 => A2]])(f1f2a: F1[F2[A1]]): F1[F2[A2]] =
      F1.ap(F1.map(f1f2f) { (f2f: F2[A1 => A2]) => (f2a: F2[A1]) => F2.ap(f2f)(f2a) })(f1f2a)

end Applicative
