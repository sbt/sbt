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
	def map[T](f: In => T): Task[T]
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
	def doFinally(t: Task[Unit]): Task[S]

	def || [T >: S](alt: Task[T]): Task[T]
	def && [T](alt: Task[T]): Task[T]
}
sealed trait TaskInfo[S]
{
	def describedAs(s: String): Task[S]
	def named(s: String): Task[S]
}
sealed trait ForkTask[S, CC[_]]
{
	def fork[T](f: S => T): CC[Task[T]]
	def tasks: Seq[Task[S]]
}
sealed trait JoinTask[S, CC[_]]
{
	def join: Task[CC[S]]
	def reduce(f: (S,S) => S): Task[S]
}

sealed trait BinaryPipe
{
	def binary[T](f: BufferedInputStream => T): Task[T]
	def binary[T](sid: String)(f: BufferedInputStream => T): Task[T]
	def #>(f: File): Task[Unit]
	def #>(sid: String, f: File): Task[Unit]
}
sealed trait TextPipe
{
	def text[T](f: BufferedReader => T): Task[T]
	def text[T](sid: String)(f: BufferedReader => T): Task[T]
}
sealed trait TaskLines
{
	def lines: Task[List[String]]
	def lines(sid: String): Task[List[String]]
}
sealed trait ProcessPipe
{
	def #| (p: ProcessBuilder): Task[Int]
	def pipe(sid: String)(p: ProcessBuilder): Task[Int]
}

trait TaskExtra
{
	final def nop: Task[Unit] = const( () )
	final def const[T](t: T): Task[T] = task(t)

	final implicit def t2ToMulti[A,B](t: (Task[A],Task[B])) = multInputTask(t._1 :^: t._2 :^: KNil)
	final implicit def f2ToHfun[A,B,R](f: (A,B) => R): (A :+: B :+: HNil => R) = { case a :+: b :+: HNil => f(a,b) }
	
	final implicit def t3ToMulti[A,B,C](t: (Task[A],Task[B],Task[C])) = multInputTask(t._1 :^: t._2 :^: t._3 :^: KNil)
	final implicit def f3ToHfun[A,B,C,R](f: (A,B,C) => R): (A :+: B :+: C :+: HNil => R) = { case a :+: b :+: c :+: HNil => f(a,b,c) }
	
	final implicit def actionToTask[T](a:  Action[T]): Task[T] = Task(Info(), a)
	
	final def task[T](f: => T): Task[T] = toTask(f _)
	final implicit def toTask[T](f: () => T): Task[T] = new Pure(f)

	final implicit def upcastTask[A >: B, B](t: Task[B]): Task[A] = t map { x => x : B }
	final implicit def toTasks[S](in: Seq[() => S]): Seq[Task[S]] = in.map(toTask)
	final implicit def iterableTask[S](in: Seq[S]): ForkTask[S, Seq] = new ForkTask[S, Seq] {
		def fork[T](f: S => T): Seq[Task[T]] = in.map(x => task(f(x)))
		def tasks: Seq[Task[S]] = fork(idFun)
	}

	final implicit def joinAnyTasks(in: Seq[Task[_]]): JoinTask[Any, Seq] = joinTasks[Any](in map (x => x: Task[Any]))
	final implicit def joinTasks[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
		def join: Task[Seq[S]] = new Join(in, (s: Seq[Result[S]]) => Right(TaskExtra.all(s)) )
		def reduce(f: (S,S) => S): Task[S] = TaskExtra.reduce(in.toIndexedSeq, f)
	}
	
		import TaskExtra.{allM, anyFailM, failM, successM}	
	final implicit def multInputTask[In <: HList](tasks: Tasks[In]): MultiInTask[In] = new MultiInTask[In] {
		def flatMapR[T](f: Results[In] => Task[T]): Task[T] = new FlatMapped(tasks, f)
		def flatMap[T](f: In => Task[T]): Task[T] = flatMapR(f compose allM)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T] = flatMapR(f compose anyFailM)

		def mapR[T](f: Results[In] => T): Task[T] = new Mapped(tasks, f)
		def map[T](f: In => T): Task[T] = mapR(f compose allM)
		def mapFailure[T](f: Seq[Incomplete] => T): Task[T] = mapR(f compose anyFailM)
	}

	final implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] = new SingleInTask[S] {
		type HL = S :+: HNil
		private val ml = in :^: KNil
		private def headM = (_: Results[HL]).combine.head
		
		def flatMapR[T](f: Result[S] => Task[T]): Task[T] = new FlatMapped[T, HL](ml, f ∙ headM)
		def mapR[T](f: Result[S] => T): Task[T] = new Mapped[T, HL](ml, f ∙ headM)
		def dependsOn(tasks: Task[_]*): Task[S] = new DependsOn(in, tasks)
		
		def flatMap[T](f: S => Task[T]): Task[T] = flatMapR(f compose successM)
		def flatFailure[T](f: Incomplete => Task[T]): Task[T] = flatMapR(f compose failM)
		
		def map[T](f: S => T): Task[T] = mapR(f compose successM)
		def mapFailure[T](f: Incomplete => T): Task[T] = mapR(f compose failM)
		
		def andFinally(fin: => Unit): Task[S] = mapR(x => Result.tryValue[S]( { fin; x }))
		def doFinally(t: Task[Unit]): Task[S] = flatMapR(x => t.mapR { tx => Result.tryValues[S](tx :: Nil, x) })
		def || [T >: S](alt: Task[T]): Task[T] = flatMapR { case Value(v) => task(v); case Inc(i) => alt }
		def && [T](alt: Task[T]): Task[T] = flatMap( _ => alt )
	}

	final implicit def toTaskInfo[S](in: Task[S]): TaskInfo[S] = new TaskInfo[S] {
		def describedAs(s: String): Task[S] = in.copy(info = in.info.setDescription(s))
		def named(s: String): Task[S] = in.copy(info = in.info.setName(s))
	}

	final implicit def pipeToProcess[Key](t: Task[_])(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): ProcessPipe = new ProcessPipe {
		def #| (p: ProcessBuilder): Task[Int] = pipe0(None, p)
		def pipe(sid: String)(p: ProcessBuilder): Task[Int] = pipe0(Some(sid), p)
		private def pipe0(sid: Option[String], p: ProcessBuilder): Task[Int] =
			for(s <- streams) yield {
				val in = s.readBinary(key(t), sid)
				val pio = TaskExtra.processIO(s).withInput( out => { BasicIO.transferFully(in, out); out.close() } )
				(p run pio).exitValue
			}
	}

	final implicit def binaryPipeTask[Key](in: Task[_])(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): BinaryPipe = new BinaryPipe {
		def binary[T](f: BufferedInputStream => T): Task[T] = pipe0(None, f)
		def binary[T](sid: String)(f: BufferedInputStream => T): Task[T] = pipe0(Some(sid), f)
		
		def #>(f: File): Task[Unit] = pipe0(None, toFile(f))
		def #>(sid: String, f: File): Task[Unit] = pipe0(Some(sid), toFile(f))
		
		private def pipe0 [T](sid: Option[String], f: BufferedInputStream => T): Task[T] =
			streams map { s => f(s.readBinary(key(in), sid)) }
		
		private def toFile(f: File) = (in: InputStream) => IO.transfer(in, f)
	}
	final implicit def textPipeTask[Key](in: Task[_])(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): TextPipe = new TextPipe {
		def text[T](f: BufferedReader => T): Task[T] = pipe0(None, f)
		def text [T](sid: String)(f: BufferedReader => T): Task[T] = pipe0(Some(sid), f)
		
		private def pipe0 [T](sid: Option[String], f: BufferedReader => T): Task[T] =
			streams map { s => f(s.readText(key(in), sid)) }
	}
	final implicit def linesTask[Key](in: Task[_])(implicit streams: Task[TaskStreams[Key]], key: Task[_] => Key): TaskLines = new TaskLines {
		def lines: Task[List[String]] = lines0(None)
		def lines(sid: String): Task[List[String]] = lines0(Some(sid))
		
		private def lines0 [T](sid: Option[String]): Task[List[String]] =
			streams map { s => IO.readLines(s.readText(key(in), sid) ) }
	}
	implicit def processToTask(p: ProcessBuilder)(implicit streams: Task[TaskStreams[_]]): Task[Int] = streams map { s =>
		val pio = TaskExtra.processIO(s)
		(p run pio).exitValue
	}
}
object TaskExtra extends TaskExtra
{
	def processIO(s: TaskStreams[_]): ProcessIO =
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
		(a :^: b :^: KNil) map { case x :+: y :+: HNil => f(x,y) }

	def anyFailM[In <: HList]: Results[In] => Seq[Incomplete] = in =>
	{
		val incs = failuresM(in)
		if(incs.isEmpty) expectedFailure else incs
	}
	def failM[T]: Result[T] => Incomplete = { case Inc(i) => i; case x => expectedFailure }

	def expectedFailure = throw Incomplete(None, message = Some("Expected dependency to fail."))

	def successM[T]: Result[T] => T = { case Inc(i) => throw i; case Value(t) => t }
	def allM[In <: HList]: Results[In] => In = in =>
	{
		val incs = failuresM(in)
		if(incs.isEmpty) in.down(Result.tryValue) else throw incompleteDeps(incs)
	}
	def failuresM[In <: HList]: Results[In] => Seq[Incomplete] = x => failures[Any](x.toList)

	def all[D](in: Seq[Result[D]]) =
	{
		val incs = failures(in)
		if(incs.isEmpty) in.map(Result.tryValue.fn[D]) else throw incompleteDeps(incs)
	}
	def failures[A](results: Seq[Result[A]]): Seq[Incomplete] = results.collect { case Inc(i) => i }

	def incompleteDeps(incs: Seq[Incomplete]): Incomplete = Incomplete(None, causes = incs)
}