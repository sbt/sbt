/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
 package sbt

/** An enumeration defining the levels available for logging.  A level includes all of the levels
* with id larger than its own id.  For example, Warn (id=3) includes Error (id=4).*/
object Level extends Enumeration
{
	val Debug = Value(1, "debug")
	val Info = Value(2, "info")
	val Warn = Value(3, "warn")
	val Error = Value(4, "error")
	/** Defines the label to use for success messages.  
	* Because the label for levels is defined in this module, the success label is also defined here. */
	val SuccessLabel = "success"

	def union(a: Value, b: Value) = if(a.id < b.id) a else b
	def unionAll(vs: Seq[Value]) = vs reduceLeft union

	/** Returns the level with the given name wrapped in Some, or None if no level exists for that name. */
	def apply(s: String) = values.find(s == _.toString)
	/** Same as apply, defined for use in pattern matching. */
	private[sbt] def unapply(s: String) = apply(s)
}