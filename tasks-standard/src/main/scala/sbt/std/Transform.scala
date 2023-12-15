/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import sbt.internal.Action
import sbt.internal.util.{ ~>, DelegatingPMap, RMap }
import sbt.internal.util.TupleMapExtension.*
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

  def dummyMap(dummyMap: DummyTaskMap): TaskId ~>| Task = {
    val pmap = new DelegatingPMap[TaskId, Task](new collection.mutable.ListMap)
    def add[T](dummy: TaskAndValue[T]): Unit = {
      pmap(dummy.task) = fromDummyStrict(dummy.task, dummy.value)
    }
    dummyMap.mappings.foreach(x => add(x))
    pmap
  }

  /** Applies `map`, returning the result if defined or returning the input unchanged otherwise. */
  implicit def getOrId(map: TaskId ~>| Task): [A] => TaskId[A] => Task[A] =
    [A] => (in: TaskId[A]) => map(in).getOrElse(in.asInstanceOf)

  def apply(dummies: DummyTaskMap) = taskToNode(getOrId(dummyMap(dummies)))

  def taskToNode(pre: [A] => TaskId[A] => Task[A]): NodeView =
    new NodeView:
      import Action.*
      def apply[T](t: TaskId[T]): Node[T] = pre(t).work match
        case Pure(eval, _)       => uniform(Nil)(_ => Right(eval()))
        case m: Mapped[a, t]     => toNode(m.in)(right[a].compose(m.f))
        case m: FlatMapped[a, t] => toNode(m.in)(left[Task[a]].compose(m.f))
        case s: Selected[a1, a2] =>
          val m = Action.asFlatMapped[a1, a2](s)
          toNode(m.in)(left[Task[a2]].compose(m.f))
        case DependsOn(in, deps) => uniform(existToAny(deps))(const(Left(in)) compose all)
        case Join(in, f)         => uniform(in)(f)

      def inline1[T](t: TaskId[T]): Option[() => T] = t match
        case Task(_, Action.Pure(eval, true)) => Some(eval)
        case _                                => None

  def uniform[A1, D](tasks: Seq[Task[D]])(
      f: Seq[Result[D]] => Either[Task[A1], A1]
  ): Node[A1] =
    new Node[A1]:
      type Inputs = Seq[Result[D]]
      def dependencies: List[TaskId[D]] = tasks.toList
      def computeInputs(f: [a] => (x: TaskId[a]) => Result[a]): Inputs = tasks.map(f[D])
      def work(inputs: Inputs) = f(inputs)

  def toNode[A1, Tup <: Tuple](
      deps: Tuple.Map[Tup, TaskId]
  )(f: Tuple.Map[Tup, Result] => Either[Task[A1], A1]): Node[A1] =
    new Node[A1]:
      type Inputs = Tuple.Map[Tup, Result]
      def dependencies: List[TaskId[?]] = deps.iterator.toList
      def computeInputs(f: [a] => TaskId[a] => Result[a]) = deps.transform(f)
      def work(inputs: Inputs) = f(inputs)

end Transform
