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

	// TODO: replace implementation with call to new version
	def topologicalSort[T](root: T)(dependencies: T => Iterable[T]): List[T] = topologicalSort(root :: Nil)(dependencies)
	
	def topologicalSort[T](nodes: Iterable[T])(dependencies: T => Iterable[T]): List[T] =
	{
		val discovered = new mutable.HashSet[T]
		val finished = asSet(new java.util.LinkedHashSet[T])

		def visitAll(nodes: Iterable[T]) = nodes foreach visit
		def visit(dag : T){
			if (!discovered(dag)) {
				discovered(dag) = true; 
				visitAll(dependencies(dag));
				finished += dag;
			}
			else if(!finished(dag))
				throw new Cyclic(dag)
		}

		visitAll(nodes);
	
		finished.toList;
	}
	final class Cyclic(val value: Any) extends Exception("Cyclic reference involving " + value)
}

