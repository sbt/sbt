/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import std._
import Path._
import TaskExtra._
import scala.collection.{mutable, JavaConversions}

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
		val context = ReflectiveContext(this, (x: SingleProject) => Some("project")) // TODO: project names
		val dummies = new Transform.Dummies(In, State, Streams)
		def name(t: Task[_]): String = context.staticName(t) getOrElse std.Streams.name(t)
		val injected = new Transform.Injected( input, state, std.Streams(t => streamBase / name(t), (t, writer) => ConsoleLogger() ) )
		context.forName(input.name) map { t => (t.merge.map(_ => state), Transform(dummies, injected, context) ) }
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

trait PrintTask
{
	def input: Task[Input]
	lazy val show = input flatMap { in =>
		val m = ReflectUtilities.allVals[Task[_]](this)
		val taskStrings = in.splitArgs map { name =>
			m(name).merge.map {
				case Seq() => "No result for " + name
				case Seq( (conf, v) ) => name + ": " + v.toString
				case confs => confs map { case (conf, v) => conf + ": " + v  } mkString(name + ":\n\t", "\n\t", "\n")
			}
		}
		taskStrings.join.map { _ foreach println  }
	}
}
/*
trait LogManager
{
	def makeLogger(context: Context): (Task[_], PrintWriter) => Logger
}
sealed trait Properties
{
	def parent: Option[Properties]
	def get[T](key: AttributeKey[T]): Option[T]
	def sub(name: String): Properties
	def set[T](key: AttributeKey[T], value: T): Properties
	def s
}
sealed trait Attribute[T]
{
	def default: Option[T]
	def sub(s: String): Option[Attribute[T]]
	def value(s: String): Option[T]
	
	def get(path: List[String]): Option[T]
	def set(path: List[String], value: T): Attribute[T]
	def setDefault(path: List[String], default: T): Attribute[T]
}
final class MapBackedAttribute[T](val default: Option[T], subs: Map[String, MapBackedAttribute[T]], values: Map[String, T]) extends Attribute[T]
{
	def sub(s: String) = subs get s
	def value(s: String) = values get s
	
	def get(path: List[String]): Option[T] =
		path match {
			case Nil => None
			case x :: Nil => values get x
			case h :: t => (subs get h) flatMap (_ get t ) orElse default
		}
	def set(path: List[String], value: T): Attribute[T] =
		path match {
			case Nil => this
			case x :: Nil => new MapBackedAttribute(default, subs, values updated( x, value) )
			case h :: t =>
				val (newSubs, sub) = (subs get h) match {
					case Some(s) => (subs, s)
					case None => (subs.updated(h, Attribute.empty[T]), Attribute.empty[T])
				}
				newSubs put (h, sub.set(t, value)
		}
	def setDefault(path: List[String], default: T): Attribute[T] =
	
	def lastComponent(path: List[String]): Option[MapBackedAttribute[T]] =
		path match {
			case Nil => this
			case x :: Nil => new MapBackedAttribute(default, subs, values updated( x, value) )
			case h :: t =>
				val (newSubs, sub) = (subs get h) match {
					case Some(s) => (subs, s)
					case None => (subs.updated(h, Attribute.empty[T]), Attribute.empty[T])
				}
				newSubs put (h, su
		
}
trait ConsoleLogManager
{
	def makeLogger(context: Context, configuration: Configuration) = (task: Task[_], to: PrintWriter) =>
	{
		val owner = context owner task
		val taskPath = (context ownerName owner).getOrElse("") :: (context staticName task).getOrElse("") ::: Nil
		def level(key: AttributeKey[Level.Value], default: Level.Value): Level.Value = select(key) get taskPath getOrElse default
		val screenLevel = level(ScreenLogLevel, Level.Info)
		val backingLevel = select(PersistLogLevel, Level.Debug)
		
		val console = ConsoleLogger()
		val backed = ConsoleLogger(to, useColor = false) // TODO: wrap this with a filter that strips ANSI codes
		
		val multi = new MultiLogger(console :: backed :: Nil)
			// sets multi to the most verbose for clients that inspect the current level
		multi setLevel Level.union(backingLevel, screenLevel)
			// set the specific levels
		console setLevel screenLevel
		backed setLevel backingLevel
		multi: Logger
	}
}
*/
object ReflectiveContext
{
	import Transform.Context
	def apply[Owner <: AnyRef : Manifest](context: Owner, name: Owner => Option[String]): Context[Owner] = new Context[Owner]
	{
		private[sbt] lazy val tasks: Map[String, Task[_]] = ReflectUtilities.allVals[Task[_]](context).toMap
		private[sbt] lazy val reverseName: collection.Map[Task[_], String] = reverseMap(tasks)
		private[sbt] lazy val sub: collection.Map[String, Owner] = ReflectUtilities.allVals[Owner](context)
		private[sbt] lazy val reverseSub: collection.Map[Owner, String] = reverseMap(sub)

		def forName(s: String): Option[Task[_]] = tasks get s
		val staticName: Task[_] => Option[String] = reverseName.get _
		val ownerName = name
		val owner = (_: Task[_]) => Some(context)
		val subs = (o: Owner) => Nil
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