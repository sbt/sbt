/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import org.scalacheck._
import Prop._

object RelationTest extends Properties("Relation")
{
	property("Added entry check") = forAll { (pairs: List[(Int, Double)]) =>
		val r = Relation.empty[Int, Double] ++ pairs
		check(r, pairs)
	}
	def check(r: Relation[Int, Double], pairs: Seq[(Int, Double)]) =
	{
		val _1s = pairs.map(_._1).toSet
		val _2s = pairs.map(_._2).toSet
		
		r._1s == _1s && r.forwardMap.keySet == _1s &&
			r._2s == _2s && r.reverseMap.keySet == _2s &&
			pairs.forall { case (a, b) =>
				(r.forward(a) contains b) &&
				(r.reverse(b) contains a) &&
				(r.forwardMap(a) contains b) &&
				(r.reverseMap(b) contains a)
			}
	}
	
	property("Does not contain removed entries") = forAll { (pairs: List[(Int, Double, Boolean)]) =>
		val add = pairs.map { case (a,b,c) => (a,b) }
		val added = Relation.empty[Int, Double] ++ add
		
		val removeFine = pairs.collect { case (a,b,true) => (a,b) }
		val removeCoarse = removeFine.map(_._1)
		val r = added -- removeCoarse
		
		def notIn[X,Y](map: Map[X, Set[Y]], a: X, b: Y) = map.get(a).forall(set => ! (set contains b) )
		
		all(removeCoarse) { rem =>
			("_1s does not contain removed" |: (!r._1s.contains(rem)) ) &&
			("Forward does not contain removed" |: r.forward(rem).isEmpty ) &&
			("Forward map does not contain removed" |: !r.forwardMap.contains(rem) ) &&
			("Removed is not a value in reverse map" |: !r.reverseMap.values.toSet.contains(rem) )
		} &&
		all(removeFine) { case (a, b) =>
			("Forward does not contain removed" |: ( !r.forward(a).contains(b) ) ) &&
			("Reverse does not contain removed" |: ( !r.reverse(b).contains(a) ) ) &&
			("Forward map does not contain removed" |: ( notIn(r.forwardMap, a, b) ) ) &&
			("Reverse map does not contain removed" |: ( notIn(r.reverseMap, b, a) ) )
		}
	}

	property("Groups correctly") = forAll { (entries: List[(Int, Double)], randomInt: Int) =>
		val splitInto = randomInt % 10 + 1  // Split into 1-10 groups.
		val rel = Relation.empty[Int, Double] ++ entries
		val grouped = rel groupBy (_._1 % splitInto)
		all(grouped.toSeq) {
			case (k, rel_k) => rel_k._1s forall { _ % splitInto == k }
		}
	}

	def all[T](s: Seq[T])(p: T => Prop): Prop =
		if(s.isEmpty) true else s.map(p).reduceLeft(_ && _)
}

object EmptyRelationTest extends Properties("Empty relation")
{
	lazy val e = Relation.empty[Int, Double]

	property("Forward empty") = forAll { (i: Int) => e.forward(i).isEmpty }
	property("Reverse empty") = forAll { (i: Double) => e.reverse(i).isEmpty }
	property("Forward map empty") = e.forwardMap.isEmpty
	property("Reverse map empty") = e.reverseMap.isEmpty
	property("_1 empty") = e._1s.isEmpty
	property("_2 empty") = e._2s.isEmpty
}