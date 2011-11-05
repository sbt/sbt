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
	import scala.collection.{mutable, JavaConversions};
	import JavaConversions.{asIterable, asSet}

	def topologicalSort[T](root: T)(dependencies: T => Iterable[T]): List[T] = topologicalSort(root :: Nil)(dependencies)
	
	def topologicalSort[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] =
	{
		val discovered = new mutable.HashSet[T]
		val finished = asSet(new java.util.LinkedHashSet[T])

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
	def topologicalSortUnchecked[T](node: T)(dependencies: T => Iterable[T]): List[T] =
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

		visit(node);
		finished;
	}
	final class Cyclic(val value: Any, val all: List[Any], val complete: Boolean)
		extends Exception( "Cyclic reference involving " +
			(if(complete) all.mkString("\n   ", "\n   ", "") else value) 
		)
	{
		def this(value: Any) = this(value, value :: Nil, false)
		def ::(a: Any): Cyclic = 
			if(complete)
				this
			else if(a == value)
				new Cyclic(value, all, true)
			else
				new Cyclic(value, a :: all, false)
	}
}

