/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import std._
	import Path._
	import TaskExtra._
	import Types._
	import OutputUtil._
	import java.io.{BufferedReader,File}
	import java.util.regex.Pattern

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
		mapLast(s, ctx, i.arguments) { reader =>
			val out = s.text()
			foreachLine(reader) { line =>
				out.println(line)
				println(line)
			}
		}
	}

	lazy val grepLast = (streams, input, context) flatMap { case s :+: i :+: ctx :+: HNil =>
		val i2 = new Input(i.arguments, None)
		val pattern = Pattern.compile(i2.arguments)
		mapLast(s, ctx, i2.name) { reader =>
			val out = s.text()
			foreachLine(reader) { line =>
				showMatches(pattern, line) foreach { line =>
					out.println(line)
					println(line)
				}
			}
		}
	}
}
object OutputUtil
{
	def showMatches(pattern: Pattern, line: String): Option[String] =
	{
		val matcher = pattern.matcher(line)
		if(ConsoleLogger.formatEnabled)
		{
			val highlighted = matcher.replaceAll(scala.Console.RED + "$0" + scala.Console.RESET)
			if(highlighted == line) None else Some(highlighted)
		}
		else if(matcher.find)
			Some(line)
		else
			None
	}
	def mapLast[T](s: TaskStreams, ctx: Transform.Context[Project], taskName: String)(f: BufferedReader => T): Task[Task.Cross[T]] =
	{
		val task = taskForName(ctx, taskName)
		s.readText( task, update = false ).map( f ).merge
	}
	def foreachLine(reader: BufferedReader)(f: String => Unit)
	{
		def readL()
		{
			val line = reader.readLine()
			if(line ne null) {
				f(line)
				readL()
			}
		}
		readL()
	}
	def taskForName(ctx: Transform.Context[Project], name: String): Task[_] =
		ctx.static(ctx.rootOwner, MultiProject.transformName(name)).getOrElse(error("No task '" + name + "'"))
}
trait Exec
{
	def input: Task[Input]
	def streams: Task[TaskStreams]
	def fork(p: Seq[String], log: Logger): Unit = fork(Process(p), log)
	def fork(p: ProcessBuilder, log: Logger): Unit =
	{
		val exitValue = p ! log
		if(exitValue != 0) error("Nonzero exit value: " + exitValue)
	}
	def forkWithCode(p: ProcessBuilder, log: Logger): Task[Int] = streams map { s => p ! log }
	
	lazy val sh = (input, streams) map { case in :+: s :+: HNil => fork("sh" :: "-c" :: in.arguments :: Nil, s.log) }
	lazy val exec = (input, streams) map { case in :+: s :+: HNil => fork( Process(in.splitArgs), s.log ) }
}
trait Javap extends Exec
{
	def javapTask(classpath: Task[Seq[File]], scalaInstance: Task[ScalaInstance]): Task[Unit] =
		javapTask( (classpath, scalaInstance) map { case cp :+: si :+: HNil => cp ++ si.jars } )
	def javapTask(classpath: Task[Seq[File]]): Task[Unit] =
		(input, classpath, streams) map { case in :+: cp :+: s :+: HNil =>
			val args = in.splitArgs
			val argsWithClasspath = if(cp.isEmpty) args else "-classpath" +: Path.makeString(cp) +: args
			fork("javap" +: argsWithClasspath, s.log)
		}
}