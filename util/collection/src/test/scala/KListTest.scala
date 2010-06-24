/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

object KTest {
  val f = new (Option ~> List) { def apply[T](o: Option[T]): List[T] = o.toList }

  val x = Some(3) :^: Some("asdf") :^: KNil
  val y = x map f
  val m1a = y match { case List(3) :^: List("asdf") :^: KNil => println("true") }
  val m1b = (List(3) :^: KNil)  match { case yy :^: KNil => println("true") }

  val head = new (List ~> Id) { def apply[T](xs: List[T]): T = xs.head }
  val z = y down head
  val m2 = z match { case 3 :+: "asdf" :+: HNil => println("true") }
}
