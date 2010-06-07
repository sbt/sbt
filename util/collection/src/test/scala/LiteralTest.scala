/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

// compilation test
object LiteralTest {
  def x[A[_],B[_]](f: A ~> B) = f

  import Param._
  val f = x { (p: Param[Option,List]) => p.ret( p.in.toList ) }

  val a: List[Int] = f( Some(3) )
  val b: List[String] = f( Some("aa") )
}