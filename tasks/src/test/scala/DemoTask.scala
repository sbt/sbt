/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._
import Task._
import Execute._

sealed trait Task[+T]
sealed case class Pure[+T](eval: () => T) extends Task[T]
final case class Mapped[+T, In <: HList](in: Tasks[In], f: Results[In] => T) extends Task[T]
final case class MapAll[+T, In <: HList](in: Tasks[In], f: In => T) extends Task[T]
final case class FlatMapAll[+T, In <: HList](in: Tasks[In], f: In => Task[T]) extends Task[T]
final case class MapFailure[+T, In <: HList](in: Tasks[In], f: Seq[Incomplete] => T) extends Task[T]
final case class FlatMapFailure[+T, In <: HList](in: Tasks[In], f: Seq[Incomplete] => Task[T]) extends Task[T]
final case class FlatMapped[+T, In <: HList](in: Tasks[In], f: Results[In] => Task[T]) extends Task[T]
final case class DependsOn[+T](in: Task[T], deps: Seq[Task[_]]) extends Task[T]
final case class Join[+T, U](in: Seq[Task[U]], f: Seq[U] => Either[Task[T], T]) extends Task[T] { type Uniform = U }

trait MultiInTask[In <: HList]
{
	def flatMap[T](f: In => Task[T]): Task[T]
	def flatMapR[T](f: Results[In] => Task[T]): Task[T]
	def mapH[T](f: In => T): Task[T]
	def mapR[T](f: Results[In] => T): Task[T]
	def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T]
	def mapFailure[T](f: Seq[Incomplete] => T): Task[T]
}
trait SingleInTask[S]
{
	def flatMapR[T](f: Result[S] => Task[T]): Task[T]
	def flatMap[T](f: S => Task[T]): Task[T]
	def map[T](f: S => T): Task[T]
	def mapR[T](f: Result[S] => T): Task[T]
	def flatFailure[T](f: Incomplete => Task[T]): Task[T]
	def mapFailure[T](f: Incomplete => T): Task[T]
	def dependsOn(tasks: Task[_]*): Task[S]
}
trait ForkTask[S, CC[_]]
{
	def fork[T](f: S => T): CC[Task[T]]
}
trait JoinTask[S, CC[_]]
{
	def join: Task[CC[S]]
	def reduce(f: (S,S) => S): Task[S]
}
object Task
{
	type Tasks[HL <: HList] = KList[Task, HL]
	type Results[HL <: HList] = KList[Result, HL]

	def pure[T](f: => T): Task[T] = toPure(f _)
	def pure[T](name: String, f: => T): Task[T] = new Pure(f _) { override def toString = name }
	implicit def toPure[T](f: () => T): Task[T] = new Pure(f)

	implicit def pureTasks[S](in: Seq[S]): Seq[Task[S]] = in.map(s => pure(s))
	implicit def toTasks[S](in: Seq[() => S]): Seq[Task[S]] = in.map(toPure)
	implicit def iterableTask[S](in: Seq[S]): ForkTask[S, Seq] = new ForkTask[S, Seq] {
		def fork[T](f: S => T): Seq[Task[T]] = in.map(x => pure(x) map f)
	}
	implicit def pureJoin[S](in: Seq[S]): JoinTask[S, Seq] = joinTasks(pureTasks(in))
	implicit def joinTasks[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
		def join: Task[Seq[S]] = new Join(in, (s: Seq[S]) => Right(s) )
		//def join[T](f: Iterable[S] => T): Task[Iterable[T]] = new MapAll( MList.fromTCList[Task](in), ml => f(ml.toList))
		//def joinR[T](f: Iterable[Result[S]] => T): Task[Iterable[Result[T]]] = new Mapped( MList.fromTCList[Task](in), ml => f(ml.toList))
		def reduce(f: (S,S) => S): Task[S] = Task.reduce(in.toIndexedSeq, f)
	}
	

	implicit def multInputTask[In <: HList](tasks: Tasks[In]): MultiInTask[In] = new MultiInTask[In] {
		def flatMap[T](f: In => Task[T]): Task[T] = new FlatMapAll(tasks, f)
		def flatMapR[T](f: Results[In] => Task[T]): Task[T] = new FlatMapped(tasks, f)
		def mapH[T](f: In => T): Task[T] = new MapAll(tasks, f)
		def mapR[T](f: Results[In] => T): Task[T] = new Mapped(tasks, f)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T] = new FlatMapFailure(tasks, f)
		def mapFailure[T](f: Seq[Incomplete] => T): Task[T] = new MapFailure(tasks, f)
	}
	implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] = new SingleInTask[S] {
		type HL = S :+: HNil
		private val ml = in :^: KNil
		private def headM = (_: Results[HL]).combine.head
		private def headH = (_: HL).head
		private def headS = (_: Seq[Incomplete]).head
		def flatMapR[T](f: Result[S] => Task[T]): Task[T] = new FlatMapped[T, HL](ml, f ∙ headM)
		def flatMap[T](f: S => Task[T]): Task[T] = new FlatMapAll(ml, f ∙ headH)
		def map[T](f: S => T): Task[T] = new MapAll(ml, f ∙ headH)
		def mapR[T](f: Result[S] => T): Task[T] = new Mapped[T, HL](ml, f ∙ headM)
		def flatFailure[T](f: Incomplete => Task[T]): Task[T] = new FlatMapFailure(ml, f ∙ headS)
		def mapFailure[T](f: Incomplete => T): Task[T] = new MapFailure(ml, f ∙ headS)
		def dependsOn(tasks: Task[_]*): Task[S] = new DependsOn(in, tasks)
	}

	implicit val taskToNode = new (Task ~> NodeT[Task]#Apply) {
		def apply[T](t: Task[T]): Node[Task, T] = t match {
			case Pure(eval) => toNode[T, HNil](KNil, _ => Right(eval()) )
			case Mapped(in, f) => toNode(in, right ∙ f  )
			case MapAll(in, f) => toNode[T, in.Raw](in, right ∙ (f compose allM) )
			case MapFailure(in, f) => toNode[T, in.Raw](in, right ∙ (f compose failuresM))
			case FlatMapped(in, f) => toNode(in, left ∙ f )
			case FlatMapAll(in, f) => toNode[T, in.Raw](in, left ∙ (f compose allM) )
			case FlatMapFailure(in, f) => toNode[T, in.Raw](in, left ∙ (f compose failuresM))
			case DependsOn(in, tasks) => join[T, Any](tasks, (_: Seq[Result[_]]) => Left(in))
			case j@ Join(in, f) => join[T, j.Uniform](in, f compose all)
		}
	}
	def join[T, D](tasks: Seq[Task[D]], f: Seq[Result[D]] => Either[Task[T], T]): Node[Task, T] = new Node[Task, T] {
		type Mixed = HNil
		val mixedIn = KNil
		type Uniform = D
		val uniformIn = tasks
		def work(mixed: Results[HNil], uniform: Seq[Result[Uniform]]) = {
			val inc = failures(uniform)
			if(inc.isEmpty) f(uniform) else throw Incomplete(None, causes = inc)
		}
	}
	def toNode[T, In <: HList](in: Tasks[In], f: Results[In] => Either[Task[T], T]): Node[Task, T] = new Node[Task, T] {
		type Mixed = In
		val mixedIn = in
		type Uniform = Nothing
		val uniformIn = Nil
		def work(results: Results[In], units: Seq[Result[Uniform]]) = f(results)
	}
	def allM[In <: HList]: Results[In] => In = in =>
	{
		val incs = failuresM(in)
		if(incs.isEmpty) in.down(Result.tryValue) else throw Incomplete(None, causes = incs)
	}
	def all[D]: Seq[Result[D]] => Seq[D] = in =>
	{
		val incs = failures(in)
		if(incs.isEmpty) in.map(Result.tryValue.apply[D]) else throw Incomplete(None, causes = incs)
	}
	def failuresM[In <: HList]: Results[In] => Seq[Incomplete] = x => failures[Any](x.toList)
	def failures[A]: Seq[Result[A]] => Seq[Incomplete] = _.collect { case Inc(i) => i }
	
	def run[T](root: Task[T], checkCycles: Boolean, maxWorkers: Int): Result[T] =
	{
		val (service, shutdown) = CompletionService[Task[_], Completed](maxWorkers)
		
		val x = new Execute[Task](checkCycles)(taskToNode)
		try { x.run(root)(service) } finally { shutdown() }
	}
	def tryRun[T](root: Task[T], checkCycles: Boolean, maxWorkers: Int): T =
		run(root, checkCycles, maxWorkers) match {
			case Value(v) => v
			case Inc(i) => throw i
		}
		
	def reduce[S](i: IndexedSeq[Task[S]], f: (S, S) => S): Task[S] =
		i match
		{
			case Seq() => error("Cannot reduce empty sequence") 
			case Seq(x) => x
			case Seq(x, y) => reducePair(x, y, f)
			case z =>
				val (a, b) = i.splitAt(i.size / 2)
				reducePair( reduce(a, f), reduce(b, f), f )
		}
	def reducePair[S](a: Task[S], b: Task[S], f: (S, S) => S): Task[S] =
		(a :^: b :^: KNil) mapH { case x :+: y :+: HNil => f(x,y) }
}