/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

// Action, Task, and Info are intentionally invariant in their type parameter.
//  Various natural transformations used, such as PMap, require invariant type constructors for correctness

/** Defines a task computation */
enum Action[A]:
  // TODO: remove after deprecated InputTask constructors are removed
  // private[sbt] def mapTask(f: [A1] => Task[A1] => Task[A1]): Action[A]

  /**
   * A direct computation of a value. If `inline` is true, `f` will be evaluated on the scheduler
   * thread without the overhead of normal scheduling when possible. This is intended as an
   * optimization for already evaluated values or very short pure computations.
   */
  case Pure[A](f: () => A, `inline`: Boolean) extends Action[A]
  // private[sbt] def mapTask(f: [A1] => Task[A1] => Task[A1]) = this

  /** Applies a function to the result of evaluating a heterogeneous list of other tasks. */
  case Mapped[A, Tup <: Tuple](in: Tuple.Map[Tup, Task], f: Tuple.Map[Tup, Result] => A)
      extends Action[A]
// private[sbt] def mapTask(g: Task ~> Task) = Mapped[A, K](alist.transform(in, g), f, alist)

  /** Computes another task to evaluate based on results from evaluating other tasks. */
  case FlatMapped[A, Tup <: Tuple](
      in: Tuple.Map[Tup, Task],
      f: Tuple.Map[Tup, Result] => Task[A]
  ) extends Action[A]
  // private[sbt] def mapTask(g: Task ~> Task) =
  //  FlatMapped[A, K](alist.transform(in, g), g.fn[A] compose f, alist)

  /** A computation `in` that requires other tasks `deps` to be evaluated first. */
  case DependsOn[A](in: Task[A], deps: Seq[Task[?]]) extends Action[A]
  // private[sbt] def mapTask(g: Task ~> Task) = DependsOn[A](g(in), deps.map(t => g(t)))

  /**
   * A computation that operates on the results of a homogeneous list of other tasks. It can either
   * return another task to be evaluated or the final value.
   */
  case Join[A, U](in: Seq[Task[U]], f: Seq[Result[U]] => Either[Task[A], A]) extends Action[A]
  // private[sbt] def mapTask(g: Task ~> Task) =
  //   Join[A, U](in.map(g.fn[U]), sr => f(sr).left.map(g.fn[A]))

  /**
   * A computation that conditionally falls back to a second transformation. This can be used to
   * encode `if` conditions.
   */
  case Selected[A1, A2](fab: Task[Either[A1, A2]], fin: Task[A1 => A2]) extends Action[A2]

// private[sbt] def mapTask(g: Task ~> Task) =
//  Selected[A, B](g(fab), g(fin))

end Action

object Action:
  import sbt.std.TaskExtra.*

  /**
   * Encode this computation as a flatMap.
   */
  private[sbt] def asFlatMapped[A1, A2](
      s: Action.Selected[A1, A2]
  ): Action.FlatMapped[A2, Tuple1[Either[A1, A2]]] =
    val f: Either[A1, A2] => Task[A2] = {
      case Right(b) => task(b)
      case Left(a)  => singleInputTask(s.fin).map(_(a))
    }
    Action.FlatMapped[A2, Tuple1[Either[A1, A2]]](
      Tuple1(s.fab),
      { case Tuple1(r) => f.compose(successM)(r) },
    )
end Action
