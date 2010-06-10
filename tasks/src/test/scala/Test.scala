/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._
import Node._
import Task._
import Execute._

sealed trait Task[+T]
sealed case class Pure[+T](eval: () => T) extends Task[T]
sealed case class Mapped[+T, In <: MList[Task]](in: In, f: In#Map[Result] => T) extends Task[T]
sealed case class MapAll[+T, In <: MList[Task]](in: In, f: In#Map[Result]#Raw => T) extends Task[T]
sealed case class FlatMapAll[+T, In <: MList[Task]](in: In, f: In#Map[Result]#Raw => Task[T]) extends Task[T]
sealed case class FlatMapped[+T, In <: MList[Task]](in: In, f: In#Map[Result] => Task[T]) extends Task[T]

object Task
{
	implicit val taskToNode = new (Task ~> NodeT[Task]#Apply) {
		def apply[T](t: Task[T]): Node[Task, T] = t match {
			case Pure(eval) => toNode[T, MNil](MNil, _ => Right(eval()) )
			case Mapped(in, f) => toNode[T, in.type](in, right ∙ f  )
			case MapAll(in, f) => toNode[T, in.type](in, right ∙ (f compose all) )
			case FlatMapAll(in, f) => toNode[T, in.type](in, left ∙ (f compose all) )
			case FlatMapped(in, f) => toNode[T, in.type](in, left ∙ f )
		}
	}
	def toNode[T, In <: MList[Task]](in: In, f: In#Map[Result] => Either[Task[T], T]): Node[Task, T] = new Node[Task, T] {
		type Inputs = In
		val inputs = in
		def unitDependencies = Nil
		def work(results: Results, units: UnitResults[Task]) = f(results)
	}

	def pure[T](name: String)(f: => T): Pure[T] = new Pure(f _) { override def toString = name }
	def mapped[T, In0 <: MList[Task]](name: String)(in0: In0)(f0: In0#Map[Result] => T): Mapped[T, In0] = new Mapped(in0, f0) { override def toString = name }
	def flat[T, In0 <: MList[Task]](name: String)(in0: In0)(f0: In0#Map[Result] => Task[T]): FlatMapped[T, In0] = new FlatMapped(in0, f0) {  override def toString = name }
	def mapAll[T, In0 <: MList[Task]](name: String)(in0: In0)(f0: In0#Map[Result]#Raw => T): MapAll[T, In0] = new MapAll(in0, f0) {  override def toString = name }
	def flatAll[T, In0 <: MList[Task]](name: String)(in0: In0)(f0: In0#Map[Result]#Raw => Task[T]): FlatMapAll[T, In0] = new FlatMapAll(in0, f0) {  override def toString = name }

	def all[In <: MList[Result]]: In => In#Raw = in =>
	{
		val incs = in.toList.collect { case Inc(i) => i }
		if(incs.isEmpty) in.down(Result.tryValue) else throw Incomplete(causes = incs)
	}
}
object Test
{
	val a = pure("a")(3)
	val b = pure[Boolean]("b")(error("test"))
	val b2 = pure("b2")(true)
	val c = pure("x")("asdf")
	val i3 = a :^: b :^: c :^: MNil
	val i32 = a :^: b2 :^: c :^: MNil

	val fh= (_: Int :+: Boolean :+: String :+: HNil) match
		{ case aa :+: bb :+: cc :+: HNil => aa + " " + bb + " " + cc }
	val h1 = mapAll("h1")(i3)(fh)
	val h2 = mapAll("h2")(i32)(fh)

	val f: i3.Map[Result] => Any = {
		case Value(aa) :^: Value(bb) :^: Value(cc) :^: MNil => aa + " " + bb + " " + cc
		case x =>
			val cs = x.toList.collect { case Inc(x) => x } // workaround for double definition bug
			throw Incomplete(causes = cs)
	}
	val d2 = mapped("d2")(i32)(f)
	val f2: i3.Map[Result] => Task[Any] = {
		case Value(aa) :^: Value(bb) :^: Value(cc) :^: MNil => new Pure(() => aa + " " + bb + " " + cc)
		case x => d3
	}
	lazy val d = flat("d")(i3)(f2)
	val f3: i3.Map[Result] => Task[Any] = {
		case Value(aa) :^: Value(bb) :^: Value(cc) :^: MNil => new Pure(() => aa + " " + bb + " " + cc)
		case x => d2
	}
	lazy val d3= flat("d3")(i3)(f3)

	def d4(i: Int): Task[Int] = flat("d4")(MNil){ _ => val x = math.random; if(x < 0.01) pure(x.toString)(i); else d4(i+1) }

	lazy val pureEval =
		new (Pure ~> Result) {
			def apply[T](p: Pure[T]): Result[T] =
				try { Value(p.eval()) }
				catch { case e: Exception => throw Incomplete(Incomplete.Error, directCause = Some(e)) }
		}

	lazy val resultA = d.f( d.in.map(pureEval) )

	def execute[T](root: Task[T]) = {
		val (service, shutdown) = CompletionService[Task[_], Completed](2)
		implicit val wrapped = CompletionService.manage(service)(x => println("Starting: " + x), x => println("Finished: " + x) )

		val x = new Execute[Task](true)(taskToNode)
		try { x.run(root) } finally { shutdown(); println(x.dump) }
	}

	def go()
	{
		def run[T](root: Task[T]) = 
			println("Result : " + execute(root))
	
		run(a)
		run(b)
		run(b2)
		run(c)
		run(d)
		run(d2)
		run( d4(0) )
		run(h1)
		run(h2)
	}
}