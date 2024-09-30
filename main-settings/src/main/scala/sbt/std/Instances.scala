/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import Def.Initialize
import sbt.util.{ Applicative, Monad }
import sbt.internal.util.Types.const
import sbt.internal.util.complete.{ DefaultParsers, Parser }

object InitializeInstance:
  given initializeMonad: Monad[Initialize] with
    type F[x] = Initialize[x]

    override def pure[A1](a: () => A1): Initialize[A1] = Def.pure(a)
    override def map[A1, A2](in: Initialize[A1])(f: A1 => A2): Initialize[A2] = in(f)
    override def ap[A1, A2](ff: Initialize[A1 => A2])(fa: Initialize[A1]): Initialize[A2] =
      Def.ap[A1, A2](ff)(fa)
    override def flatMap[A1, A2](fa: Initialize[A1])(f: A1 => Initialize[A2]) =
      Def.flatMap[A1, A2](fa)(f)
end InitializeInstance

object ParserInstance:
  type F1[x] = State => Parser[x]
  // import sbt.internal.util.Classes.Applicative
  // private[this] implicit val parserApplicative: Applicative[M] = new Applicative[M] {
  //   def apply[S, T](f: M[S => T], v: M[S]): M[A1] = s => (f(s) ~ v(s)) map { case (a, b) => a(b) }
  //   def pure[S](s: => S) = const(Parser.success(s))
  //   def map[S, T](f: S => T, v: M[S]) = s => v(s).map(f)
  // }

  given parserFunApplicative: Applicative[F1] with
    type F[x] = State => Parser[x]
    override def pure[A1](a: () => A1): State => Parser[A1] = const(DefaultParsers.success(a()))
    override def ap[A1, A2](ff: F[A1 => A2])(fa: F[A1]): F[A2] =
      (s: State) => (ff(s) ~ fa(s)).map { case (f, a) => f(a) }
    override def map[A1, A2](fa: F[A1])(f: A1 => A2) =
      (s: State) => fa(s).map(f)
end ParserInstance

/** Composes the Task and Initialize Instances to provide an Instance for [A1] Initialize[Task[A1]]. */
object FullInstance:
  type SS = sbt.internal.util.Settings[Scope]
  val settingsData = TaskKey[SS](
    "settings-data",
    "Provides access to the project data for the build.",
    KeyRanks.DTask
  )

  val F1F2: Applicative[[a] =>> Initialize[Task[a]]] = Applicative.given_Applicative_F1(using
    InitializeInstance.initializeMonad,
    Task.taskMonad
  )
  given initializeTaskMonad: Monad[[a] =>> Initialize[Task[a]]] with
    type F[x] = Initialize[Task[x]]
    override def pure[A1](x: () => A1): Initialize[Task[A1]] = F1F2.pure(x)

    override def map[A1, A2](fa: Initialize[Task[A1]])(f: A1 => A2): Initialize[Task[A2]] =
      F1F2.map(fa)(f)

    override def ap[A1, A2](ff: Initialize[Task[A1 => A2]])(
        fa: Initialize[Task[A1]]
    ): Initialize[Task[A2]] =
      F1F2.ap(ff)(fa)

    override def flatMap[A1, A2](fa: Initialize[Task[A1]])(
        f: A1 => Initialize[Task[A2]]
    ): Initialize[Task[A2]] =
      val nested: Initialize[Task[Initialize[Task[A2]]]] = F1F2.map(fa)(f)
      flatten(nested)

    override def flatten[A1](in: Initialize[Task[Initialize[Task[A1]]]]): Initialize[Task[A1]] =
      FullInstance.flatten[A1](in)

  def flatten[A1](in: Initialize[Task[Initialize[Task[A1]]]]): Initialize[Task[A1]] =
    type Tup = (Task[Initialize[Task[A1]]], Task[SS], [a] => Initialize[a] => Initialize[a])
    Def.app[Tup, Task[A1]]((in, settingsData, Def.capturedTransformations)) {
      case (a: Task[Initialize[Task[A1]]], data: Task[SS], f) =>
        TaskExtra.multT2Task((a, data)).flatMapN { case (a, d) => f(a) evaluate d }
    }

  def flattenFun[A1, A2](
      in: Initialize[Task[A1 => Initialize[Task[A2]]]]
  ): Initialize[A1 => Task[A2]] =
    type Tup = (Task[A1 => Initialize[Task[A2]]], Task[SS], [a] => Initialize[a] => Initialize[a])
    Def.app[Tup, A1 => Task[A2]]((in, settingsData, Def.capturedTransformations)) {
      case (a: Task[A1 => Initialize[Task[A2]]] @unchecked, data: Task[SS] @unchecked, f) =>
        (s: A1) => TaskExtra.multT2Task((a, data)).flatMapN { case (af, d) => f(af(s)) evaluate d }
    }

end FullInstance
