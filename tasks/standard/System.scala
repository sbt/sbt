/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package std

import Types._
import Task._
import Execute._

object System
{
	def fromDummy[T](original: Task[T])(action: => T): Task[T] = Task(original.info, Pure(action _))
	def fromDummyStrict[T](original: Task[T], value: T): Task[T] = fromDummy(original)( value)

	implicit def to_~>| [K[_], V[_]](map: RMap[K,V]) : K ~>| V = new (K ~>| V) { def apply[T](k: K[T]): Option[V[T]] = map.get(k) }

	def dummyMap[HL <: HList](dummies: KList[Task, HL])(inject: HL): Task ~>| Task =
	{
		val pmap = new DelegatingPMap[Task, Task](new collection.mutable.ListMap)
		def loop[HL <: HList](ds: KList[Task, HL], vs: HL): Unit =
			(ds, vs) match {
				case (KCons(dh, dt), vh :+: vt) =>
					pmap(dh) = fromDummyStrict(dh, vh)
					loop(dt, vt)
				case _ => ()
			}
		loop(dummies, inject)
		pmap
	}

	/** Applies `map`, returning the result if defined or returning the input unchanged otherwise.*/
	implicit def getOrId(map: Task ~>| Task): Task ~> Task =
		new (Task ~> Task) {
			def apply[T](in: Task[T]): Task[T]  =  map(in).getOrElse(in)
		}
}
object Transform
{
	final class Dummies[HL <: HList](val direct: KList[Task, HL])

	def apply[HL <: HList, Key](dummies: KList[Task, HL], injected: HL) =
	{
		import System._
		Convert.taskToNode ∙ getOrId(dummyMap(dummies)(injected))
	}
}
object Convert
{
	def taskToNode = new (Task ~> NodeT[Task]#Apply) {
		def apply[T](t: Task[T]): Node[Task, T] = t.work match {
			case Pure(eval) => toNode(KNil)( _ => Right(eval()) )
			case Mapped(in, f) => toNode(in)( right ∙ f  )
			case FlatMapped(in, f) => toNode(in)( left ∙ f )
			case DependsOn(in, deps) => toNode(KList.fromList(deps))( _ => Left(in) )
			case Join(in, f) => uniform(in)(f)
		}
	}
		
	def uniform[T, D](tasks: Seq[Task[D]])(f: Seq[Result[D]] => Either[Task[T], T]): Node[Task, T] = new Node[Task, T] {
		type Mixed = HNil
		val mixedIn = KNil
		type Uniform = D
		val uniformIn = tasks
		def work(mixed: Results[HNil], uniform: Seq[Result[Uniform]]) = f(uniform)
	}
	def toNode[T, In <: HList](in: Tasks[In])(f: Results[In] => Either[Task[T], T]): Node[Task, T] = new Node[Task, T] {
		type Mixed = In
		val mixedIn = in
		type Uniform = Nothing
		val uniformIn = Nil
		def work(results: Results[In], units: Seq[Result[Uniform]]) = f(results)
	}
}