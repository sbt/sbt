/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import org.scalacheck._
import Prop._

object RelationTest extends Properties("Relation")
{
	property("Added entry check") = forAll { (pairs: List[(Int, Int)]) =>
		val r = Relation.empty[Int] ++ pairs
		check(r, pairs)
	}
	def check(r: Relation[Int], pairs: Seq[(Int, Int)]) =
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
	
	property("Does not contain removed entries") = forAll { (pairs: List[(Int, Int, Boolean)]) =>
		val add = pairs.map { case (a,b,c) => (a,b) }
		val added = Relation.empty[Int] ++ add
		
		val removeFine = pairs.collect { case (a,b,true) => (a,b) }
		val removeCoarse = removeFine.map(_._1)
		val r = added -- removeCoarse
		
		def notIn[T](map: Map[T, Set[T]], a: T, b: T) = map.get(a).forall(set => ! (set contains b) )
		
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
	def all[T](s: Seq[T])(p: T => Prop): Prop =
		if(s.isEmpty) true else s.map(p).reduceLeft(_ && _)
}

object EmptyRelationTest extends Properties("Empty relation")
{
	lazy val e = Relation.empty[Int]

	property("Forward empty") = forAll { (i: Int) => e.forward(i).isEmpty }
	property("Reverse empty") = forAll { (i: Int) => e.reverse(i).isEmpty }
	property("Forward map empty") = forAll { (i: Int) => e.forwardMap.isEmpty }
	property("Reverse map empty") = forAll { (i: Int) => e.reverseMap.isEmpty }
	property("_1 empty") = forAll { (i: Int) => e._1s.isEmpty }
	property("_2 empty") = forAll { (i: Int) => e._2s.isEmpty }
}