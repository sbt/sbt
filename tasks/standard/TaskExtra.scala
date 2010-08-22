/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package std

import Types._
import Task._
import java.io.{BufferedInputStream, BufferedReader, File, InputStream}


sealed trait MultiInTask[In <: HList]
{
	def flatMap[T](f: In => Task[T]): Task[T]
	def flatMapR[T](f: Results[In] => Task[T]): Task[T]
	def mapH[T](f: In => T): Task[T]
	def mapR[T](f: Results[In] => T): Task[T]
	def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T]
	def mapFailure[T](f: Seq[Incomplete] => T): Task[T]
}
sealed trait SingleInTask[S]
{
	def flatMapR[T](f: Result[S] => Task[T]): Task[T]
	def flatMap[T](f: S => Task[T]): Task[T]
	def map[T](f: S => T): Task[T]
	def mapR[T](f: Result[S] => T): Task[T]
	def flatFailure[T](f: Incomplete => Task[T]): Task[T]
	def mapFailure[T](f: Incomplete => T): Task[T]
	def dependsOn(tasks: Task[_]*): Task[S]
	def andFinally(fin: => Unit): Task[S]

	def || [T >: S](alt: Task[T]): Task[T]
	def && [T](alt: Task[T]): Task[T]
}
sealed trait TaskInfo[S]
{
	def named(s: String): Task[S]
	def describedAs(s: String): Task[S]
	def implies: Task[S]
}
sealed trait ForkTask[S, CC[_]]
{
	def fork[T](f: S => T): CC[Task[T]]
}
sealed trait JoinTask[S, CC[_]]
{
	def join: Task[CC[S]]
	def reduce(f: (S,S) => S): Task[S]
}
import java.io._
sealed trait BinaryPipe
{
	def binary[T](f: BufferedInputStream => T): Task[T]
	//def binary[T](sid: String)(f: BufferedInputStream => T): Task[T]
	def #>(f: File): Task[Unit]
	//def #>(sid: String, f: File): Task[Unit]
}
sealed trait TextPipe
{
	def text[T](f: BufferedReader => T): Task[T]
	//def #| [T](sid: String)(f: BufferedReader => T): Task[T]
}
sealed trait TaskLines
{
	def lines: Task[List[String]]
	def lines(sid: String): Task[List[String]]
}
sealed trait ProcessPipe {
	def #| (p: ProcessBuilder): Task[Int]
	//def #| (sid: String)(p: ProcessBuilder): Task[Int]
}

trait TaskExtra
{
	final implicit def actionToTask[A <% Action[T], T](a: A): Task[T] = Task(Info(), a)
	
	final def task[T](f: => T): Task[T] = toTask(f _)
	final implicit def toTask[T](f: () => T): Task[T] = new Pure(f)

	final implicit def pureTasks[S](in: Seq[S]): Seq[Task[S]] = in.map(s => task(s))
	final implicit def toTasks[S](in: Seq[() => S]): Seq[Task[S]] = in.map(toTask)
	final implicit def iterableTask[S](in: Seq[S]): ForkTask[S, Seq] = new ForkTask[S, Seq] {
		def fork[T](f: S => T): Seq[Task[T]] = in.map(x => task(x) map f)
	}
	final implicit def pureJoin[S](in: Seq[S]): JoinTask[S, Seq] = joinTasks(pureTasks(in))
	final implicit def joinTasks[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
		def join: Task[Seq[S]] = new Join(in, (s: Seq[S]) => Right(s) )
		//def join[T](f: Iterable[S] => T): Task[Iterable[T]] = new MapAll( MList.fromTCList[Task](in), ml => f(ml.toList))
		//def joinR[T](f: Iterable[Result[S]] => T): Task[Iterable[Result[T]]] = new Mapped( MList.fromTCList[Task](in), ml => f(ml.toList))
		def reduce(f: (S,S) => S): Task[S] = TaskExtra.reduce(in.toIndexedSeq, f)
	}

	final implicit def multInputTask[In <: HList](tasks: Tasks[In]): MultiInTask[In] = new MultiInTask[In] {
		def flatMap[T](f: In => Task[T]): Task[T] = new FlatMapAll(tasks, f)
		def flatMapR[T](f: Results[In] => Task[T]): Task[T] = new FlatMapped(tasks, f)
		def mapH[T](f: In => T): Task[T] = new MapAll(tasks, f)
		def mapR[T](f: Results[In] => T): Task[T] = new Mapped(tasks, f)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T] = new FlatMapFailure(tasks, f)
		def mapFailure[T](f: Seq[Incomplete] => T): Task[T] = new MapFailure(tasks, f)
	}
	
	final implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] = new SingleInTask[S] {
		type HL = S :+: HNil
		private val ml = in :^: KNil
		private def headM = (_: Results[HL]).combine.head
		private def headH = (_: HL).head
		private def headS = (_: Seq[Incomplete]).head
		
		def flatMapR[T](f: Result[S] => Task[T]): Task[T] = new FlatMapped[T, HL](ml, f ∙ headM)
		def flatMap[T](f: S => Task[T]): Task[T] = new FlatMapAll(ml, f ∙ headH)
		def flatFailure[T](f: Incomplete => Task[T]): Task[T] = new FlatMapFailure(ml, f ∙ headS)
		
		def map[T](f: S => T): Task[T] = new MapAll(ml, f ∙ headH)
		def mapR[T](f: Result[S] => T): Task[T] = new Mapped[T, HL](ml, f ∙ headM)
		def mapFailure[T](f: Incomplete => T): Task[T] = new MapFailure(ml, f ∙ headS)
		
		def dependsOn(tasks: Task[_]*): Task[S] = new DependsOn(in, tasks)
		def andFinally(fin: => Unit): Task[S] = mapR(x => Result.tryValue[S]( { fin; x }))
		
		def || [T >: S](alt: Task[T]): Task[T] = flatMapR { case Value(v) => task(v); case Inc(i) => alt }
		def && [T](alt: Task[T]): Task[T] = flatMap( _ => alt )
	}
	final implicit def toTaskInfo[S](in: Task[S]): TaskInfo[S] = new TaskInfo[S] {
		def named(s: String): Task[S] = in.copy(info = in.info.copy(name = Some(s)))
		def describedAs(s: String): Task[S] = in.copy(info = in.info.copy(description = Some(s)))
		def implies: Task[S] = in.copy(info = in.info.copy(implied = true))
	}

	final implicit def pipeToProcess(t: Task[_])(implicit streams: Task[TaskStreams]): ProcessPipe = new ProcessPipe {
		def #| (p: ProcessBuilder): Task[Int] = pipe0(None, p)
		//def #| (sid: String)(p: ProcessBuilder): Task[Int] = pipe0(Some(sid), p)
		private def pipe0(sid: Option[String], p: ProcessBuilder): Task[Int] =
			for(s <- streams; in <- s.readBinary(t, sid, true)) yield {
				val pio = TaskExtra.processIO(s).withInput( out => { BasicIO.transferFully(in, out); out.close() } )
				(p run pio).exitValue
			}
	}

	final implicit def binaryPipeTask(in: Task[_])(implicit streams: Task[TaskStreams]): BinaryPipe = new BinaryPipe {
		def binary[T](f: BufferedInputStream => T): Task[T] = pipe0(None, f)
		//def #| [T](sid: String)(f: BufferedInputStream => T): Task[T] = pipe0(Some(sid), f)
		
		def #>(f: File): Task[Unit] = pipe0(None, toFile(f))
		//def #>(sid: String, f: File): Task[Unit] = pipe0(Some(sid), toFile(f))
		
		private def pipe0 [T](sid: Option[String], f: BufferedInputStream => T): Task[T] =
			streams flatMap { s => s.readBinary(in, sid, true) map f }
		
		private def toFile(f: File) = (in: InputStream) => IO.transfer(in, f)
	}
	final implicit def textPipeTask(in: Task[_])(implicit streams: Task[TaskStreams]): TextPipe = new TextPipe {
		def text[T](f: BufferedReader => T): Task[T] = pipe0(None, f)
		//def #| [T](sid: String)(f: BufferedReader => T): Task[T] = pipe0(Some(sid), f)
		
		private def pipe0 [T](sid: Option[String], f: BufferedReader => T): Task[T] =
			streams flatMap { s => s.readText(in, sid, true) map f }
	}
	final implicit def linesTask(in: Task[_])(implicit streams: Task[TaskStreams]): TaskLines = new TaskLines {
		def lines: Task[List[String]] = lines0(None)
		def lines(sid: String): Task[List[String]] = lines0(Some(sid))
		
		private def lines0 [T](sid: Option[String]): Task[List[String]] =
			streams flatMap { s => s.readText(in, sid, true) map IO.readLines }
	}
	implicit def processToTask(p: ProcessBuilder)(implicit streams: Task[TaskStreams]): Task[Int] = streams map { s =>
		val pio = TaskExtra.processIO(s)
		(p run pio).exitValue
	}
}
object TaskExtra extends TaskExtra
{
	def processIO(s: TaskStreams): ProcessIO =
	{
		def transfer(id: String) = (in: InputStream) => BasicIO.transferFully(in, s.binary(id))
		new ProcessIO(BasicIO.ignoreOut, transfer(s.outID), transfer(s.errorID))
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
		(a :^: b :^: KNil) mapH { case x :+: y :+: HNil => f(x,y) }
}