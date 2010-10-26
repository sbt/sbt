/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import std._
	import Path._
	import TaskExtra._
	import scala.collection.{immutable, mutable, JavaConversions}

	import java.io.File

trait SingleProject extends Tasked with PrintTask with TaskExtra with Types
{
	def base = new File(".")
	def streamBase = base / "streams"

	implicit def streams = Dummy.Streams
	def input = Dummy.In
	def state = Dummy.State

	type Task[T] = sbt.Task[T]
	def act(input: Input, state: State): Option[(Task[State], Execute.NodeView[Task])] =
	{
		import Dummy._
		val context = ReflectiveContext(this, "project")
		val dummies = new Transform.Dummies(In, State, Streams)
		def name(t: Task[_]): String = context.staticName(t) getOrElse std.Streams.name(t)
		val injected = new Transform.Injected( input, state, std.Streams(t => streamBase / name(t), (t, writer) => ConsoleLogger() ) )
		context.static(this, input.name) map { t => (t.merge.map(_ => state), Transform(dummies, injected, context) ) }
	}

	def help: Seq[Help] = Nil
}
object Dummy
{
	val InName = "command-line-input"
	val StateName = "command-state"
	val StreamsName = "task-streams"
	
	def dummy[T](name: String): Task[T] = task( error("Dummy task '" + name + "' did not get converted to a full task.") )  named name
	val In = dummy[Input](InName)
	val State = dummy[State](StateName)
	val Streams = dummy[TaskStreams](StreamsName)
}

object ReflectiveContext
{
	import Transform.Context
	def apply[Owner <: AnyRef : Manifest](context: Owner, name: String): Context[Owner] = new Context[Owner]
	{
		private[sbt] lazy val tasks: immutable.SortedMap[String, Task[_]] = ReflectUtilities.allVals[Task[_]](context)
		private[sbt] lazy val reverseName: collection.Map[Task[_], String] = reverseMap(tasks)
		private[sbt] lazy val sub: Map[String, Owner] = ReflectUtilities.allVals[Owner](context)
		private[sbt] lazy val reverseSub: collection.Map[Owner, String] = reverseMap(sub)

		val staticName: Task[_] => Option[String] = reverseName.get _
		val ownerName = (o: Owner) => if(o eq context) Some(name) else None
		val owner = (t: Task[_]) => if(reverseName contains t) Some(context) else None
		def allTasks(o: Owner): Seq[Task[_]] = if(o eq context) tasks.values.toSeq else Nil
		def ownerForName(oname: String): Option[Owner] = if(name == oname) Some(context) else None
		val aggregate = (_: Owner) => Nil
		val static = (o: Owner, s: String) => if(o eq context) tasks.get(s) else None

		private def reverseMap[A,B](in: Iterable[(A,B)]): collection.Map[B,A] =
		{
			import JavaConversions._
			val map: mutable.Map[B, A] = new java.util.IdentityHashMap[B, A]
			for( (name, task) <- in ) map(task) = name
			map
		}
	}
}