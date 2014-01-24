/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 David MacIver, Mark Harrah
 */
package sbt;

trait Dag[Node <: Dag[Node]]{
	self : Node =>

	def dependencies : Iterable[Node]
	def topologicalSort = Dag.topologicalSort(self)(_.dependencies)
}
object Dag
{
	import scala.collection.{mutable, JavaConverters}
	import JavaConverters.asScalaSetConverter

	def topologicalSort[T](root: T)(dependencies: T => Iterable[T]): List[T] = topologicalSort(root :: Nil)(dependencies)

	def topologicalSort[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] =
	{
		val discovered = new mutable.HashSet[T]
		val finished = (new java.util.LinkedHashSet[T]).asScala

		def visitAll(nodes: Iterable[T]) = nodes foreach visit
		def visit(node : T){
			if (!discovered(node)) {
				discovered(node) = true;
				try { visitAll(dependencies(node)); } catch { case c: Cyclic => throw node :: c }
				finished += node;
			}
			else if(!finished(node))
				throw new Cyclic(node)
		}

		visitAll(nodes);

		finished.toList;
	}
	// doesn't check for cycles
	def topologicalSortUnchecked[T](node: T)(dependencies: T => Iterable[T]): List[T] = topologicalSortUnchecked(node :: Nil)(dependencies)

	def topologicalSortUnchecked[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] =
	{
		val discovered = new mutable.HashSet[T]
		var finished: List[T] = Nil

		def visitAll(nodes: Iterable[T]) = nodes foreach visit
		def visit(node : T){
			if (!discovered(node)) {
				discovered(node) = true;
				visitAll(dependencies(node))
				finished ::= node;
			}
		}

		visitAll(nodes);
		finished;
	}
	final class Cyclic(val value: Any, val all: List[Any], val complete: Boolean)
		extends Exception( "Cyclic reference involving " +
			(if(complete) all.mkString("\n   ", "\n   ", "") else value)
		)
	{
		def this(value: Any) = this(value, value :: Nil, false)
		override def toString = getMessage
		def ::(a: Any): Cyclic =
			if(complete)
				this
			else if(a == value)
				new Cyclic(value, all, true)
			else
				new Cyclic(value, a :: all, false)
	}

	private[sbt] trait System[A] {
		type B
		def dependencies(t: A): List[B]
		def isNegated(b: B): Boolean
		def toA(b: B): A
	}
	private[sbt] def findNegativeCycle[T](system: System[T])(nodes: List[system.B]): List[system.B] =
	{
			import scala.annotation.tailrec
			import system._
		val finished = new mutable.HashSet[T]
		val visited = new mutable.HashSet[T]

		def visit(nodes: List[B], stack: List[B]): List[B] = nodes match {
			case Nil => Nil
			case node :: tail =>
				val atom = toA(node)
				if(!visited(atom))
				{
					visited += atom
					visit(dependencies(atom), node :: stack) match {
						case Nil =>
							finished += atom
							visit(tail, stack)
						case cycle => cycle
					}
				}
				else if(!finished(atom))
				{
					// cycle. If negation is involved, it is an error.
					val between = stack.takeWhile(f => toA(f) != atom)
					if(between exists isNegated)
						between
					else
						visit(tail, stack)
				}
				else
					visit(tail, stack)
		}

		visit(nodes, Nil)
	}

}
