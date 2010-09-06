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

	def dummyMap[Input, State](dummyIn: Task[Input], dummyState: Task[State])(in: Input, state: State): Task ~>| Task =
	{
		// helps ensure that the same Task[Nothing] can't be passed for dummyIn and dummyState
		assert(dummyIn ne dummyState, "Dummy tasks for Input and State must be distinct.")
		val pmap = new DelegatingPMap[Task, Task](new collection.mutable.ListMap)
		pmap(dummyIn) = fromDummyStrict(dummyIn, in)
		pmap(dummyState) = fromDummyStrict(dummyState, state)
		pmap
	}

	/** Applies `map`, returning the result if defined or returning the input unchanged otherwise.*/
	implicit def getOrId(map: Task ~>| Task): Task ~> Task =
		new (Task ~> Task) {
			def apply[T](in: Task[T]): Task[T]  =  map(in).getOrElse(in)
		}

	def implied[Owner](owner: Task[_] => Option[Owner], subs: Owner => Iterable[Owner], static: (Owner, String) => Option[Task[_]]): Task ~> Task =
		new (Task ~> Task) {
		
			def impliedDeps(t: Task[_]): Seq[Task[_]] = 
				for( n <- t.info.name.toList; o <- owner(t.original).toList; agg <- subs(o); implied <- static(agg, n) ) yield implied

			def withImplied[T](in: Task[T]): Task[T]  =  Task(Info(), DependsOn(in, impliedDeps(in)))

			def apply[T](in: Task[T]): Task[T]  =  if(in.info.implied) withImplied(in) else in
		}

	def name(staticName: Task[_] => Option[String]): Task ~> Task =
		new (Task ~> Task) {
			def apply[T](in: Task[T]): Task[T] = {
				val finalName = in.info.name orElse staticName(in.original)
				finalName match {
					case None => in
					case Some(finalName) => in.copy(info = in.info.setName(finalName) )
				}
			}
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


	def streamed(streams: Streams, dummy: Task[TaskStreams]): Task ~> Task =
		new (Task ~> Task) {
			def apply[T](t: Task[T]): Task[T] = if(usedInputs(t.work) contains dummy) substitute(t) else t
			
			def substitute[T](t: Task[T]): Task[T] =
			{
				val inStreams = streams(t)
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
	final class Dummies[Input, State](val dummyIn: Task[Input], val dummyState: Task[State], val dummyStreams: Task[TaskStreams])
	final class Injected[Input, State](val in: Input, val state: State, val streams: Streams)
	trait Context[Owner]
	{
		def staticName: Task[_] => Option[String]
		def owner: Task[_] => Option[Owner]
		def ownerName: Owner => Option[String]
		def aggregate: Owner => Iterable[Owner]
		def static: (Owner, String) => Option[Task[_]]
		def allTasks(owner: Owner): Iterable[Task[_]]
		def ownerForName(name: String): Option[Owner]
	}
	def setOriginal(delegate: Task ~> Task): Task ~> Task =
		new (Task ~> Task) {
			def apply[T](in: Task[T]): Task[T] =
			{
				val transformed = delegate(in)
				if( (transformed eq in) || transformed.info.original.isDefined)
					transformed 
				else
					transformed.copy(info = transformed.info.copy(original = in.info.original orElse Some(in)))
			}
		}

	def apply[Input, State, Owner](dummies: Dummies[Input, State], injected: Injected[Input, State], context: Context[Owner]) =
	{
		import dummies._
		import injected._
		import context._
		import System._
		import Convert._
		val inputs = dummyMap(dummyIn, dummyState)(in, state)
		Convert.taskToNode ∙ setOriginal(streamed(streams, dummyStreams)) ∙ implied(owner, aggregate, static) ∙ setOriginal(name(staticName)) ∙ getOrId(inputs)
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
			case CrossAction(subs) => error("Cannot run cross task: " + subs.mkString("\n\t","\n\t","\n"))
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