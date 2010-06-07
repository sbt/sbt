/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

// compilation test
object PMapTest
{
	val mp = new DelegatingPMap[Some, Id](new collection.mutable.HashMap)
	mp(Some("asdf")) = "a"
	mp(Some(3)) = 9
	val x = Some(3) :^: Some("asdf") :^: MNil
	val y = x.map[Id](mp)
	val z = y.down
	z match { case 9 :+: "a" :+: HNil => println("true") }
}