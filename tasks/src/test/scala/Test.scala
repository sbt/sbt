/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._
import Task._
import Execute._

object Test
{
	val a = pure(3)
	val b = pure[Boolean](error("test"))
	val b2 = pure(true)
	val c = pure("asdf")
	val i3 = a :^: b :^: c :^: MNil
	val i32 = a :^: b2 :^: c :^: MNil

	val fh= (_: Int :+: Boolean :+: String :+: HNil) match
		{ case aa :+: bb :+: cc :+: HNil => aa + " " + bb + " " + cc }
	val h1 = i3 mapH fh
	val h2 = i32 mapH fh

	val f: i3.Map[Result] => Any = {
		case Value(aa) :^: Value(bb) :^: Value(cc) :^: MNil => aa + " " + bb + " " + cc
		case x =>
			val cs = x.toList.collect { case Inc(x) => x } // workaround for double definition bug
			throw Incomplete(causes = cs)
	}
	val d2 = i32 mapR f
	val f2: i3.Map[Result] => Task[Any] = {
		case Value(aa) :^: Value(bb) :^: Value(cc) :^: MNil => new Pure(() => aa + " " + bb + " " + cc)
		case x => d3
	}
	lazy val d = i3 flatMapR f2
	val f3: i3.Map[Result] => Task[Any] = {
		case Value(aa) :^: Value(bb) :^: Value(cc) :^: MNil => new Pure(() => aa + " " + bb + " " + cc)
		case x => d2
	}
	lazy val d3= i3 flatMapR f3

	def d4(i: Int): Task[Int] = MNil flatMap { _ => val x = math.random; if(x < 0.01) pure(i); else d4(i+1) }

	def go()
	{
		def run[T](root: Task[T]) = 
			println("Result : " + Task.run(root, true, 2))
	
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