/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import sbt.internal.Action
import sbt.internal.util.Types._
import sbt.internal.util.{ ~>, AList, DelegatingPMap, RMap }
import TaskExtra.{ all, existToAny }
import sbt.internal.util.Types.*

object Transform:
  def fromDummy[A](original: Task[A])(action: => A): Task[A] =
    Task(original.info, Action.Pure(() => action, false))

  def fromDummyStrict[T](original: Task[T], value: T): Task[T] = fromDummy(original)(value)

  implicit def to_~>|[K[_], V[_]](map: RMap[K, V]): ~>|[K, V] =
    [A] => (k: K[A]) => map.get(k)

  final case class DummyTaskMap(mappings: List[TaskAndValue[_]]) {
    def ::[T](tav: (Task[T], T)): DummyTaskMap =
      DummyTaskMap(new TaskAndValue(tav._1, tav._2) :: mappings)
  }

  final class TaskAndValue[T](val task: Task[T], val value: T)

  def dummyMap(dummyMap: DummyTaskMap): Task ~>| Task = {
    val pmap = new DelegatingPMap[Task, Task](new collection.mutable.ListMap)
    def add[T](dummy: TaskAndValue[T]): Unit = {
      pmap(dummy.task) = fromDummyStrict(dummy.task, dummy.value)
    }
    dummyMap.mappings.foreach(x => add(x))
    pmap
  }

  /** Applies `map`, returning the result if defined or returning the input unchanged otherwise. */
  implicit def getOrId(map: Task ~>| Task): [A] => Task[A] => Task[A] =
    [A] => (in: Task[A]) => map(in).getOrElse(in)

  def apply(dummies: DummyTaskMap) = taskToNode(getOrId(dummyMap(dummies)))

  def taskToNode(pre: [A] => Task[A] => Task[A]): NodeView =
    new NodeView:
      import Action.*
      def apply[T](t: Task[T]): Node[T] = pre(t).work match
        case Pure(eval, _)   => uniform(Nil)(_ => Right(eval()))
        case m: Mapped[a, k] => toNode[a, k](m.in)(right[a] compose m.f)(m.alist)
        case m: FlatMapped[a, k] =>
          toNode[a, k](m.in)(left[Task[a]] compose m.f)(m.alist) // (m.alist)
        case s: Selected[a1, a2] =>
          val m = Action.asFlatMapped[a1, a2](s)
          toNode[a2, [F[_]] =>> Tuple1[F[Either[a1, a2]]]](m.in)(left[Task[a2]] compose m.f)(
            m.alist
          )
        case DependsOn(in, deps) => uniform(existToAny(deps))(const(Left(in)) compose all)
        case Join(in, f)         => uniform(in)(f)

      def inline1[T](t: Task[T]): Option[() => T] = t.work match
        case Action.Pure(eval, true) => Some(eval)
        case _                       => None

  def uniform[A1, D](tasks: Seq[Task[D]])(
      f: Seq[Result[D]] => Either[Task[A1], A1]
  ): Node[A1] =
    toNode[A1, [F[_]] =>> List[F[D]]](tasks.toList)(f)(AList.list[D])

  def toNode[A1, K1[F[_]]: AList](
      inputs: K1[Task]
  )(f: K1[Result] => Either[Task[A1], A1]): Node[A1] =
    new Node[A1]:
      type K[F[_]] = K1[F]
      val in = inputs
      lazy val alist: AList[K] = AList[K]
      def work(results: K[Result]) = f(results)

end Transform
