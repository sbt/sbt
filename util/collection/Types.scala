/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

object Types extends Types

trait Types extends TypeFunctions
{
	val :^: = KCons
	type :+:[H, T <: HList] = HCons[H,T]
	val :+: = HCons
}
