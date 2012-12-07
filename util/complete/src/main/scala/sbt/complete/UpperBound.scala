/* sbt -- Simple Build Tool
 * Copyright 2008,2010  Mark Harrah
 */
package sbt.complete

sealed trait UpperBound
{
	/** True if and only if the given value meets this bound.*/
	def >=(min: Int): Boolean
	/** True if and only if this bound is one.*/
	def isOne: Boolean
	/** True if and only if this bound is zero.*/
	def isZero: Boolean
	/** If this bound is zero or Infinite, `decrement` returns this bound.
	* Otherwise, this bound is finite and greater than zero and `decrement` returns the bound that is one less than this bound.*/
	def decrement: UpperBound
	/** True if and only if this is unbounded.*/
	def isInfinite: Boolean
}
/** Represents unbounded. */
case object Infinite extends UpperBound
{
	/** All finite numbers meet this bound. */
	def >=(min: Int) = true
	def isOne = false
	def isZero = false
	def decrement = this
	def isInfinite = true
	override def toString = "Infinity"
}
/** Represents a finite upper bound. The maximum allowed value is 'value', inclusive.
*  It must positive. */
final case class Finite(value: Int) extends UpperBound
{
	assume(value >= 0, "Maximum occurences must be nonnegative.")

	def >=(min: Int) = value >= min
	def isOne = value == 1
	def isZero = value == 0
	def decrement = Finite( (value - 1) max 0 )
	def isInfinite = false
	override def toString = value.toString
}
object UpperBound
{
	implicit def intToFinite(i: Int): Finite = Finite(i)
}