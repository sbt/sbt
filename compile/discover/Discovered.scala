/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

final case class Discovered(baseClasses: Set[String], annotations: Set[String], hasMain: Boolean, isModule: Boolean)
object Discovered
{
	def empty = new Discovered(Set.empty, Set.empty, false, false)
}