/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

final case class Discovered(baseClasses: Set[String], annotations: Set[String], hasMain: Boolean, isModule: Boolean)
{
	def isEmpty = baseClasses.isEmpty && annotations.isEmpty
}
object Discovered
{
	def empty = new Discovered(Set.empty, Set.empty, false, false)
}