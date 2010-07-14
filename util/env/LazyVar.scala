/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

private final class LazyVar[T](initialValue: => T) extends NotNull
{
	private[this] var value: Option[T] = None
	def apply() =
		synchronized
		{
			value match
			{
				case Some(v) => v
				case None =>
					val newValue = initialValue
					value = Some(newValue)
					newValue
			}
		}
	def update(newValue: T) = synchronized { value = Some(newValue) }
}