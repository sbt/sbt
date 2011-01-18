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

	/** Creates a natural transformation that replaces occurrences of 'a' with 'b'.
	* Only valid for M invariant in its type parameter. */
	def replace[M[_] <: AnyRef, A](a: M[A], b: M[A]) = new (M ~> M) {
		def apply[T](t: M[T]): M[T] =
			if(t eq a) b.asInstanceOf[M[T]] else t
	}

	/** Returns the inputs to the Task that do not have their value discarded.
	*  For example, this does not include the inputs of MapFailure or the dependencies of DependsOn. */
	def usedInputs(t: Action[_]): Seq[Task[_]] = t match {
		case m: Mapped[_,_] => m.in.toList
		case m: FlatMapped[_,_] => m.in.toList
		case j: Join[_,_] => j.in
		case _ => Nil
	}


	def streamed[Key](streams: Streams[Key], dummy: Task[TaskStreams[Key]], key: Task[_] => Key): Task ~> Task =
		new (Task ~> Task) {
			def apply[T](t: Task[T]): Task[T] = if(usedInputs(t.work) contains dummy) substitute(t) else t
			
			def substitute[T](t: Task[T]): Task[T] =
			{
				val inStreams = streams(key(t))
				val streamsTask = fromDummy(dummy){ inStreams.open(); inStreams }

				val depMap = replace( dummy, streamsTask )
				def wrap0[A,B](f: A => B) =  (a: A) => try { f(a) } finally { inStreams.close() }
				def fin(k: Task[T]): Task[T]  = {
					import TaskExtra._
					k andFinally { inStreams.close() }
				}
				def newWork(a: Action[T]): Task[T] = t.copy(work = a)

				t.work match {
					case m: Mapped[_,_] => newWork( m.copy(in = m.in transform depMap, f = wrap0(m.f) ) ) // the Streams instance is valid only within the mapping function
					case fm: FlatMapped[_,_] => fin(newWork( fm.copy(in = fm.in transform depMap) )) // the Streams instance is valid until a result is produced for the task
					case j: Join[_,u] => newWork( j.copy(j.in map depMap.fn[u], f = wrap0(j.f)) )
					case _ => t // can't get a TaskStreams value from the other types, so no need to transform here (shouldn't get here anyway because of usedInputs check)
				}
			}
		}
}
object Transform
{
	final class Dummies[HL <: HList, Key](val direct: KList[Task, HL], val streams: Task[TaskStreams[Key]])
	final class Injected[HL <: HList, Key](val direct: HL, val streams: Streams[Key])

	def apply[HL <: HList, Key](dummies: Dummies[HL, Key], injected: Injected[HL, Key])(implicit getKey: Task[_] => Key) =
	{
		import System._
		val inputs = dummyMap(dummies.direct)(injected.direct)
		Convert.taskToNode ∙ streamed(injected.streams, dummies.streams, getKey) ∙ getOrId(inputs)
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