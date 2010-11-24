/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import std._
	import Path._
	import TaskExtra._
	import scala.collection.{immutable, mutable, JavaConversions}

	import java.io.File

object Dummy
{
	val InName = "command-line-input"
	val StateName = "command-state"
	val StreamsName = "task-streams"
	val ContextName = "task-context"
	
	def dummy[T](name: String): Task[T] = task( error("Dummy task '" + name + "' did not get converted to a full task.") )  named name
	val In = dummy[Input](InName)
	val State = dummy[State](StateName)
	val Streams = dummy[TaskStreams](StreamsName)
	val Context = dummy[Transform.Context[Project]](ContextName)
}
/** A group of tasks, often related in some way.
* This trait should be used like:
* def testTasks(prefix: Option[String]) = new TestTasks(prefix)
* final class TestTasks(val prefix: Option[String]) extends TaskGroup {
*  lazy val test = ...
*  lazy val discover = ...
* }
*
* This allows a method to return multiple tasks that can still be referenced uniformly and directly:
* val test = testTasks(None)
* val it = testTasks(Some("integration"))
*
* > test 
* ... run test.test ...
* > integration-test
* ... run it.test ...
*/
trait TaskGroup
{
	def prefix: Option[String]
	def tasks: immutable.SortedMap[String, Task[_]] = ReflectiveContext.deepTasks(this)
}
object ReflectiveContext
{
	def deepTasks(context: AnyRef): immutable.SortedMap[String, Task[_]] =
	{
		val direct = ReflectUtilities.allVals[Task[_]](context)
		val groups = ReflectUtilities.allVals[TaskGroup](context)
		val nested = groups.flatMap { case (name, group) => prefixAll(group.prefix, group.tasks) }
		nested ++ direct // this order necessary so that direct shadows nested
	}
	def prefixAll(pre: Option[String], tasks: immutable.SortedMap[String, Task[_]]): immutable.SortedMap[String, Task[_]] =
		pre match
		{
			case Some(p) if !p.isEmpty => prefix(p, tasks)
			case _ => tasks
		}
	def prefix(p: String, tasks: Iterable[(String, Task[_])]): immutable.SortedMap[String, Task[_]] =
		immutable.TreeMap[String, Task[_]]() ++ (tasks map { case (name, task) => (p + name.capitalize, task) })

		import Transform.Context
	def apply[Owner <: AnyRef : Manifest](context: Owner, name: String, root: Owner): Context[Owner] = new Context[Owner]
	{
		private[sbt] lazy val tasks: immutable.SortedMap[String, Task[_]] = deepTasks(context)
		private[sbt] lazy val reverseName: collection.Map[Task[_], String] = reverseMap(tasks)
		private[sbt] lazy val sub: Map[String, Owner] = ReflectUtilities.allVals[Owner](context)
		private[sbt] lazy val reverseSub: collection.Map[Owner, String] = reverseMap(sub)

		def rootOwner = root
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