/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import std._
	import Path._
	import TaskExtra._

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

trait LastOutput
{
	def input: Task[Input]
	def streams: Task[TaskStreams]
	lazy val last = (streams, input) flatMap { (s: TaskStreams, i: Input) =>
		val tasks = ReflectUtilities.allVals[Task[_]](this)
		(s.readText( tasks(i.arguments) , update = false ) map { reader =>
			val out = s.text()
			def readL()
			{
				val line = reader.readLine()
				if(line ne null) {
					readL()
					out.println(line)
					println(line)
				}
			}
			readL()
		}).merge
	}
}
