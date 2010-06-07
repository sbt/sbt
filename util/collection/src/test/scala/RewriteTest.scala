/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

object RewriteTest
{
	// dist and add0 show the awkwardness when not just manipulating superstructure:
	//  would have to constrain the parameters to Term to be instances of Zero/Eq somehow
	val dist: Rewrite[Term] = (p: Param[Term, Term]) => p.ret( p.in match {
		case Add(Mult(a,b),Mult(c,d)) if a == c=> Mult(a, Add(b,d))
		case x => x
	})
	val add0: Rewrite[Term] = (p: Param[Term, Term]) => p.ret( p.in match {
		case Add(V(0), y) => y
		case Add(x, V(0)) => x
		case x => x
	})
	val rewriteBU= new Rewrite[Term] {
		def apply[T](node: Term[T], rewrite: Rewrite[Term]) = {
			def r[T](node: Term[T]) = rewrite(node, rewrite)
			node match {
				case Add(x, y) => Add(r(x), r(y))
				case Mult(x, y) => Mult(r(x), r(y))
				case x => x
			}
		}
	}

	val d2 = dist ∙ add0 ∙ rewriteBU

	implicit def toV(t: Int): V[Int] = V(t)
	implicit def toVar(s: String): Var[Int] = Var[Int](s)

	val t1: Term[Int] = Add(Mult(3,4), Mult(4, 5))
	val t2: Term[Int] = Add(Mult(4,4), Mult(4, 5))
	val t3: Term[Int] = Add(Mult(Add("x", 0),4), Mult("x", 5))

	println( Rewrite(t1)(d2) )
	println( Rewrite(t2)(d2) )
	println( Rewrite(t3)(d2) )
}

sealed trait Term[T]
final case class Add[T](a: Term[T], b: Term[T]) extends Term[T]
final case class Mult[T](a: Term[T], b: Term[T]) extends Term[T]
final case class V[T](v: T) extends Term[T]
final case class Var[T](name: String) extends Term[T]
