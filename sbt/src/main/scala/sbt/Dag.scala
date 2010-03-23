/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 David MacIver, Mark Harrah
 */
package sbt;

trait Dag[Node <: Dag[Node]]{
	self : Node =>

	def dependencies : Iterable[Node]
	def topologicalSort = Dag.topologicalSort(self)(_.dependencies)
}
object Dag
{
	import scala.collection.mutable;

	def topologicalSort[T](root: T)(dependencies: T => Iterable[T]) = {
		val discovered = new mutable.HashSet[T];
		val finished = new wrap.MutableSetWrapper(new java.util.LinkedHashSet[T])

		def visit(dag : T){
			if (!discovered(dag)) {
				discovered(dag) = true; 
				dependencies(dag).foreach(visit);
				finished += dag;
			}
		}

		visit(root);
	
		finished.toList;
	}
}

