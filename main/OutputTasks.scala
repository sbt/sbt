/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import std._
	import Path._
	import TaskExtra._
	import Types._
	import OutputUtil._

trait PrintTask
{
	def input: Task[Input]
	def context: Task[Transform.Context[Project]]
	lazy val show = (input, context) flatMap { case in :+: ctx :+: HNil =>
		val taskStrings = in.splitArgs map { name =>
			val selected = taskForName(ctx, name)
			selected.merge.map {
				case Seq() => "No result for " + name
				case Seq( (conf, v) ) => name + ": " + v.toString
				case confs => confs map { case (conf, v) => conf + ": " + v  } mkString(name + ":\n\t", "\n\t", "\n")
			}
		}
		taskStrings.join.map { _ foreach println  }
	}
}

trait LastOutput
{
	def input: Task[Input]
	def streams: Task[TaskStreams]
	def context: Task[Transform.Context[Project]]
	lazy val last = (streams, input, context) flatMap { case s :+: i :+: ctx :+: HNil =>
		val task = taskForName(ctx, i.arguments)
		(s.readText( task, update = false ) map { reader =>
			val out = s.text()
			def readL()
			{
				val line = reader.readLine()
				if(line ne null) {
					out.println(line)
					println(line)
					readL()
				}
			}
			readL()
		}).merge
	}
}
object OutputUtil
{
	def taskForName(ctx: Transform.Context[Project], name: String): Task[_] =
		ctx.static(ctx.rootOwner, MultiProject.transformName(name)).getOrElse(error("No task '" + name + "'"))
}