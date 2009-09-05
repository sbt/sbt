/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
 package xsbt

/** An enumeration defining the levels available for logging.  A level includes all of the levels
* with id larger than its own id.  For example, Warn (id=3) includes Error (id=4).*/
object Level extends Enumeration with NotNull
{
	val Debug = Value(1, "debug")
	val Info = Value(2, "info")
	val Warn = Value(3, "warn")
	val Error = Value(4, "error")
	/** Defines the label to use for success messages.  A success message is logged at the info level but
	* uses this label.  Because the label for levels is defined in this module, the success
	* label is also defined here. */
	val SuccessLabel = "success"

	// added because elements was renamed to iterator in 2.8.0 nightly
	def levels = Debug :: Info :: Warn :: Error :: Nil
	/** Returns the level with the given name wrapped in Some, or None if no level exists for that name. */
	def apply(s: String) = levels.find(s == _.toString)
	/** Same as apply, defined for use in pattern matching. */
	private[xsbt] def unapply(s: String) = apply(s)
}