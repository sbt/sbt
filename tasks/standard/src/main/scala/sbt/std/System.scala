/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package std

import Types._
import Task._
import TaskExtra.{ all, existToAny }
import Execute._

object Transform {
  def fromDummy[T](original: Task[T])(action: => T): Task[T] = Task(original.info, Pure(action _, false))
  def fromDummyStrict[T](original: Task[T], value: T): Task[T] = fromDummy(original)(value)

  implicit def to_~>|[K[_], V[_]](map: RMap[K, V]): K ~>| V = new (K ~>| V) { def apply[T](k: K[T]): Option[V[T]] = map.get(k) }

  final case class DummyTaskMap(mappings: List[TaskAndValue[_]]) {
    def ::[T](tav: (Task[T], T)): DummyTaskMap = DummyTaskMap(new TaskAndValue(tav._1, tav._2) :: mappings)
  }
  final class TaskAndValue[T](val task: Task[T], val value: T)
  def dummyMap(dummyMap: DummyTaskMap): Task ~>| Task =
    {
      val pmap = new DelegatingPMap[Task, Task](new collection.mutable.ListMap)
      def add[T](dummy: TaskAndValue[T]): Unit = { pmap(dummy.task) = fromDummyStrict(dummy.task, dummy.value) }
      dummyMap.mappings.foreach(x => add(x))
      pmap
    }

  /** Applies `map`, returning the result if defined or returning the input unchanged otherwise.*/
  implicit def getOrId(map: Task ~>| Task): Task ~> Task =
    new (Task ~> Task) {
      def apply[T](in: Task[T]): Task[T] = map(in).getOrElse(in)
    }

  def apply(dummies: DummyTaskMap) =
    {
      import System._
      taskToNode(getOrId(dummyMap(dummies)))
    }

  def taskToNode(pre: Task ~> Task): NodeView[Task] = new NodeView[Task] {
    def apply[T](t: Task[T]): Node[Task, T] = pre(t).work match {
      case Pure(eval, _)       => uniform(Nil)(_ => Right(eval()))
      case m: Mapped[t, k]     => toNode[t, k](m.in)(right ∙ m.f)(m.alist)
      case m: FlatMapped[t, k] => toNode[t, k](m.in)(left ∙ m.f)(m.alist)
      case DependsOn(in, deps) => uniform(existToAny(deps))(const(Left(in)) ∙ all)
      case Join(in, f)         => uniform(in)(f)
    }
    def inline[T](t: Task[T]) = t.work match {
      case Pure(eval, true) => Some(eval)
      case _                => None
    }
  }

  def uniform[T, D](tasks: Seq[Task[D]])(f: Seq[Result[D]] => Either[Task[T], T]): Node[Task, T] =
    toNode[T, ({ type l[L[x]] = List[L[D]] })#l](tasks.toList)(f)(AList.seq[D])

  def toNode[T, k[L[x]]](inputs: k[Task])(f: k[Result] => Either[Task[T], T])(implicit a: AList[k]): Node[Task, T] = new Node[Task, T] {
    type K[L[x]] = k[L]
    val in = inputs
    val alist = a
    def work(results: K[Result]) = f(results)
  }
}
