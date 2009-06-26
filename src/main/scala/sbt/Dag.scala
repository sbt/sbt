/* sbt -- Simple Build Tool
 * Copyright 2008 David MacIver
 */
package sbt;

import scala.collection.mutable;

trait Dag[Node <: Dag[Node]]{
	self : Node =>

	def dependencies : Iterable[Node]

	def topologicalSort = {
		val discovered = new mutable.HashSet[Node];
		val finished = new wrap.MutableSetWrapper(new java.util.LinkedHashSet[Node])

		def visit(dag : Node){
			if (!discovered(dag)) {
				discovered(dag) = true; 
				dag.dependencies.foreach(visit);
				finished += dag;
			}
		}

		visit(self);
	
		finished.toList;
	}
}

