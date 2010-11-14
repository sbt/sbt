/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package std

	import Types._
	import Task._
	import java.io.{BufferedInputStream, BufferedReader, File, InputStream}
	import Cross.{combine, crossTask, exist, expandExist, extract, hasCross, uniform}

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
sealed trait CrossMerge[T]
{
	def merge: Task[Cross[T]]
}
sealed trait TaskInfo[S]
{
	def named(s: String): Task[S]
	def describedAs(s: String): Task[S]
	def implies: Task[S]
	def implied(flag: Boolean): Task[S]
	def local: Task[S]
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
	final def cross[T](key: AttributeKey[T])(values: T*): Task[T] =
		CrossAction( for(v <- values) yield ( AttributeMap.empty put (key, v), task(v) ) )

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
		def tasks: Seq[Task[S]] = fork(identity)
	}

	final implicit def joinAnyTasks(in: Seq[Task[_]]): JoinTask[Any, Seq] = joinTasks[Any](in map (x => x: Task[Any]))
	final implicit def joinTasks[S](in: Seq[Task[S]]): JoinTask[S, Seq] =
		if(hasCross(in)) multJoin(in) else basicJoin(in)
		
	final def multJoin[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
		def join: Task[Seq[S]] = uniform(in)( (_, s) => basicJoin(s).join )
		def reduce(f: (S,S) => S): Task[S] = basicJoin(in) reduce f
	}
	final def basicJoin[S](in: Seq[Task[S]]): JoinTask[S, Seq] = new JoinTask[S, Seq] {
		def join: Task[Seq[S]] = new Join(in, (s: Seq[Result[S]]) => Right(TaskExtra.all(s)) )
		def reduce(f: (S,S) => S): Task[S] = TaskExtra.reduce(in.toIndexedSeq, f)
	}
	final implicit def crossMerge[T](in: Task[T]): CrossMerge[T] = new CrossMerge[T] {
		def merge: Task[Cross[T]] = in.work match {
			case CrossAction(subs) =>
				val (maps, tasks) = subs.unzip
				tasks.join.map { maps zip _ }
			case _ => in map( x => (AttributeMap.empty, x) :: Nil)
		}
	}
	
	final implicit def multInputTask[In <: HList](tasks: Tasks[In]): MultiInTask[In] =
		if(hasCross(tasks.toList)) multCross(tasks) else multBasic(tasks)

	final def multCross[In <: HList](tasks: Tasks[In]): MultiInTask[In] = new MultiBase[In] {
		def flatMapR[T](f: Results[In] => Task[T]): Task[T] = Cross(tasks)( (m, ts) => new FlatMapped[T,In](ts, extract[T](m) compose f) )
		def mapR[T](f: Results[In] => T): Task[T] = Cross(tasks)( (_, ts) => multBasic(ts) mapR f)
	}

	final def multBasic[In <: HList](tasks: Tasks[In]): MultiInTask[In] = new MultiBase[In] {
		def flatMapR[T](f: Results[In] => Task[T]): Task[T] = new FlatMapped(tasks, extract() ∙ f)
		def mapR[T](f: Results[In] => T): Task[T] = new Mapped(tasks, f)
	}

	final implicit def singleInputTask[S](in: Task[S]): SingleInTask[S] =
		in.work match {
			case CrossAction(subs) => singleCross(in, subs)
			case x => singleBasic(in)
		}

	final def singleCross[S](in: Task[S], subs: Cross[Task[S]]): SingleInTask[S] =
		new SingleBase[S] {
			def impl[T](f: (AttributeMap, Task[S]) => Task[T]): Task[T] = CrossAction( subs map { case (m, t) => (m, f(m, t)) } )
			def flatMapR[T](f: Result[S] => Task[T]): Task[T] = impl( (m, t) => singleBasic(t) flatMapR( Cross.extract(m) ∙ f) )
			def mapR[T](f: Result[S] => T): Task[T] = impl( (m,t) => t mapR f)
			def dependsOn(tasks: Task[_]*): Task[S] = crossTask( combine( subs, expandExist(tasks) ){ (t,deps) => new DependsOn(t, deps) } )
		}

	final def singleBasic[S](in: Task[S]): SingleInTask[S] = new SingleBase[S] {
		type HL = S :+: HNil
		private val ml = in :^: KNil
		private def headM = (_: Results[HL]).combine.head
		
		def flatMapR[T](f: Result[S] => Task[T]): Task[T] = new FlatMapped[T, HL](ml, Cross.extract() ∙ f ∙ headM)
		def mapR[T](f: Result[S] => T): Task[T] = new Mapped[T, HL](ml, f ∙ headM)
		def dependsOn(tasks: Task[_]*): Task[S] =
			if(hasCross(tasks))
				singleCross(in, (AttributeMap.empty, in) :: Nil).dependsOn(tasks :_*)
			else
				new DependsOn(in, tasks)
	}

	final implicit def toTaskInfo[S](in: Task[S]): TaskInfo[S] = new TaskInfo[S] {
		def named(s: String): Task[S] = in.copy(info = in.info.setName(s))
		def describedAs(s: String): Task[S] = in.copy(info = in.info.setDescription(s))
		def implied(flag: Boolean): Task[S] = in.copy(info = in.info.setImplied(flag))
		def implies: Task[S] = implied(true)
		def local: Task[S] = implied(false)

	}

	final implicit def pipeToProcess(t: Task[_])(implicit streams: Task[TaskStreams]): ProcessPipe = new ProcessPipe {
		def #| (p: ProcessBuilder): Task[Int] = pipe0(None, p)
		def pipe(sid: String)(p: ProcessBuilder): Task[Int] = pipe0(Some(sid), p)
		private def pipe0(sid: Option[String], p: ProcessBuilder): Task[Int] =
			for(s <- streams; in <- s.readBinary(t, sid, true)) yield {
				val pio = TaskExtra.processIO(s).withInput( out => { BasicIO.transferFully(in, out); out.close() } )
				(p run pio).exitValue
			}
	}

	final implicit def binaryPipeTask(in: Task[_])(implicit streams: Task[TaskStreams]): BinaryPipe = new BinaryPipe {
		def binary[T](f: BufferedInputStream => T): Task[T] = pipe0(None, f)
		def binary[T](sid: String)(f: BufferedInputStream => T): Task[T] = pipe0(Some(sid), f)
		
		def #>(f: File): Task[Unit] = pipe0(None, toFile(f))
		def #>(sid: String, f: File): Task[Unit] = pipe0(Some(sid), toFile(f))
		
		private def pipe0 [T](sid: Option[String], f: BufferedInputStream => T): Task[T] =
			streams flatMap { s => s.readBinary(in, sid, true) map f }
		
		private def toFile(f: File) = (in: InputStream) => IO.transfer(in, f)
	}
	final implicit def textPipeTask(in: Task[_])(implicit streams: Task[TaskStreams]): TextPipe = new TextPipe {
		def text[T](f: BufferedReader => T): Task[T] = pipe0(None, f)
		def text [T](sid: String)(f: BufferedReader => T): Task[T] = pipe0(Some(sid), f)
		
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
	
	private[this] abstract class SingleBase[S] extends SingleInTask[S]
	{
			import TaskExtra.{successM, failM}
			
		def flatMap[T](f: S => Task[T]): Task[T] = flatMapR(f compose successM)
		def flatFailure[T](f: Incomplete => Task[T]): Task[T] = flatMapR(f compose failM)
		
		def map[T](f: S => T): Task[T] = mapR(f compose successM)
		def mapFailure[T](f: Incomplete => T): Task[T] = mapR(f compose failM)
		
		def andFinally(fin: => Unit): Task[S] = mapR(x => Result.tryValue[S]( { fin; x }))
		def doFinally(t: Task[Unit]): Task[S] = flatMapR(x => t.mapR { tx => Result.tryValues[S](tx :: Nil, x) })
		def || [T >: S](alt: Task[T]): Task[T] = flatMapR { case Value(v) => task(v); case Inc(i) => alt }
		def && [T](alt: Task[T]): Task[T] = flatMap( _ => alt )
	}
	private[this] abstract class MultiBase[In <: HList] extends MultiInTask[In]
	{
			import TaskExtra.{allM, anyFailM}
			
		def flatMap[T](f: In => Task[T]): Task[T] = flatMapR(f compose allM)
		def flatFailure[T](f: Seq[Incomplete] => Task[T]): Task[T] = flatMapR(f compose anyFailM)

		def map[T](f: In => T): Task[T] = mapR(f compose allM)
		def mapFailure[T](f: Seq[Incomplete] => T): Task[T] = mapR(f compose anyFailM)
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
		(a :^: b :^: KNil) map { case x :+: y :+: HNil => f(x,y) }

	def anyFailM[In <: HList]: Results[In] => Seq[Incomplete] = in =>
	{
		val incs = failuresM(in)
		if(incs.isEmpty) expectedFailure else incs
	}
	def failM[T]: Result[T] => Incomplete = { case Inc(i) => i; case x => expectedFailure }

	def expectedFailure = throw Incomplete(message = Some("Expected failure"))

	def successM[T]: Result[T] => T = { case Inc(i) => throw i; case Value(t) => t }
	def allM[In <: HList]: Results[In] => In = in =>
	{
		val incs = failuresM(in)
		if(incs.isEmpty) in.down(Result.tryValue) else throw Incomplete(causes = incs)
	}
	def failuresM[In <: HList]: Results[In] => Seq[Incomplete] = x => failures[Any](x.toList)

	def all[D](in: Seq[Result[D]]) =
	{
		val incs = failures(in)
		if(incs.isEmpty) in.map(Result.tryValue.fn[D]) else throw Incomplete(causes = incs)
	}
	def failures[A](results: Seq[Result[A]]): Seq[Incomplete] = results.collect { case Inc(i) => i }
}