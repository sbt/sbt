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
	val x = Some(3) :^: Some("asdf") :^: KNil
	val y = x.transform[Id](mp)
	assert(y.head == 9)
	assert(y.tail.head == "a")
	assert(y.tail.tail == KNil)
}