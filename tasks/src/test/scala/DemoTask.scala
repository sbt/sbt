/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._
import Task._
import Execute._

sealed trait Task[+T]
sealed case class Pure[+T](eval: () => T) extends Task[T]
final case class Mapped[+T, In <: MList[Task]](in: In, f: In#Map[Result] => T) extends Task[T]
final case class MapAll[+T, In <: MList[Task]](in: In, f: In#Map[Result]#Raw => T) extends Task[T]
final case class FlatMapAll[+T, In <: MList[Task]](in: In, f: In#Map[Result]#Raw => Task[T]) extends Task[T]
final case class MapFailure[+T, In <: MList[Task]](in: In, f: Seq[Incomplete] => T) extends Task[T]
final case class FlatMapFailure[+T, In <: MList[Task]](in: In, f: Seq[Incomplete] => Task[T]) extends Task[T]
final case class FlatMapped[+T, In <: MList[Task]](in: In, f: In#Map[Result] => Task[T]) extends Task[T]
final case class DependsOn[+T](in: Task[T], deps: Seq[Task[_]]) extends Task[T]
final case class Join[+T, U](in: Seq[Task[U]], f: Seq[U] => Either[Task[T], T]) extends Task[T] { type Uniform = U }

trait MultiInTask[M <: MList[Task]]
{
	def flatMap[T](f: M#Map[Result]#Raw => Task[T]): Task[T]
	def flatMapR[T](f: M#Map[Result] => Task[T]): Task[T]
	def mapH[T](f: M#Map[Result]#Raw => T): Task[T]
	def mapR[T](f: M#Map[Result] => T): Task[T]
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
	

	implicit def multInputTask[M <: MList[Task]](ml: M): MultiInTask[M] = new MultiInTask[M] {
		def flatMap[T](f: M#Map[Result]#Raw => Task[T]): Task[T] = new FlatMapAll(ml, f)
		def flatMapR[T](f: M#Map[Result] => Task[T]): Task[T] = new FlatMapped(ml, f)
		def mapH[T](f: M#Map[Result]#Raw => T): Task[T] = new MapAll(ml, f)
		def mapR[T](f: M#Map[Result] => T): Task[T] = new Mapped(ml, f)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T] = new FlatMapFailure(ml, f)
		def mapFailure[T](f: Seq[Incomplete] => T): Task[T] = new MapFailure(ml, f)
	}
	implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] = new SingleInTask[S] {
		private val ml = in :^: MNil
		private def headM = (_: ml.Map[Result]).head
		private def headH = (_: S :+: HNil).head
		private def headS = (_: Seq[Incomplete]).head
		def flatMapR[T](f: Result[S] => Task[T]): Task[T] = new FlatMapped[T, ml.type](ml, f ∙ headM)
		def flatMap[T](f: S => Task[T]): Task[T] = new FlatMapAll[T, ml.type](ml, f ∙ headH)
		def map[T](f: S => T): Task[T] = new MapAll[T, ml.type](ml, f ∙ headH)
		def mapR[T](f: Result[S] => T): Task[T] = new Mapped[T, ml.type](ml, f ∙ headM)
		def flatFailure[T](f: Incomplete => Task[T]): Task[T] = new FlatMapFailure(ml, f ∙ headS)
		def mapFailure[T](f: Incomplete => T): Task[T] = new MapFailure(ml, f ∙ headS)
		def dependsOn(tasks: Task[_]*): Task[S] = new DependsOn(in, tasks)
	}

	implicit val taskToNode = new (Task ~> NodeT[Task]#Apply) {
		def apply[T](t: Task[T]): Node[Task, T] = t match {
			case Pure(eval) => toNode[T, MNil](MNil, _ => Right(eval()) )
			case Mapped(in, f) => toNode[T, in.type](in, right ∙ f  )
			case MapAll(in, f) => toNode[T, in.type](in, right ∙ (f compose allM) )
			case MapFailure(in, f) => toNode[T, in.type](in, right ∙ (f compose failuresM))
			case FlatMapped(in, f) => toNode[T, in.type](in, left ∙ f )
			case FlatMapAll(in, f) => toNode[T, in.type](in, left ∙ (f compose allM) )
			case FlatMapFailure(in, f) => toNode[T, in.type](in, left ∙ (f compose failuresM))
			case DependsOn(in, tasks) => join[T, Any](tasks, (_: Seq[Result[_]]) => Left(in))
			case j@ Join(in, f) => join[T, j.Uniform](in, f compose all)
		}
	}
	def join[T, D](tasks: Seq[Task[D]], f: Seq[Result[D]] => Either[Task[T], T]): Node[Task, T] = new Node[Task, T] {
		type Mixed = MNil
		val mixedIn = MNil
		type Uniform = D
		val uniformIn = tasks
		def work(mixed: MNil, uniform: Seq[Result[Uniform]]) = {
			val inc = failures(uniform)
			if(inc.isEmpty) f(uniform) else throw Incomplete(causes = inc)
		}
	}
	def toNode[T, In <: MList[Task]](in: In, f: In#Map[Result] => Either[Task[T], T]): Node[Task, T] = new Node[Task, T] {
		type Mixed = In
		val mixedIn = in
		type Uniform = Nothing
		val uniformIn = Nil
		def work(results: Mixed#Map[Result], units: Seq[Result[Uniform]]) = f(results)
	}
	def allM[In <: MList[Result]]: In => In#Raw = in =>
	{
		val incs = failuresM(in)
		if(incs.isEmpty) in.down(Result.tryValue) else throw Incomplete(causes = incs)
	}
	def all[D]: Seq[Result[D]] => Seq[D] = in =>
	{
		val incs = failures(in)
		if(incs.isEmpty) in.map(Result.tryValue.apply[D]) else throw Incomplete(causes = incs)
	}
	def failuresM[In <: MList[Result]]: In => Seq[Incomplete] = x => failures[Any](x.toList)
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
		(a :^: b :^: MNil) mapH { case x :+: y :+: HNil => f(x,y) }
}