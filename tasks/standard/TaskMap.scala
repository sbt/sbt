/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

	import std._
	import TaskExtra._
	import collection.mutable

object TaskMap
{
	/** Memoizes a function that produces a Task. */
	def apply[A,B](f: A => Task[B]): A => Task[B] =
	{
		val map = new mutable.HashMap[A,Task[B]]
		a => map.synchronized { map.getOrElseUpdate(a, f(a)) }
	}
	/** Lifts `f` to produce a Task and memoizes the lifted function. */
	def make[A,B](f: A => B): A => Task[B] = apply( a => task(f(a)) )
}