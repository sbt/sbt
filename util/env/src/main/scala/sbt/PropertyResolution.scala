/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

sealed trait PropertyResolution[+T] extends NotNull
{
	def value: T
	def orElse[R >: T](r: => PropertyResolution[R]): PropertyResolution[R]
	def toOption: Option[T]
	def foreach(f: T => Unit): Unit
	def map[R](f: T => R): PropertyResolution[R]
	def flatMap[R](f: T => PropertyResolution[R]): PropertyResolution[R]
}
sealed trait NoPropertyValue extends PropertyResolution[Nothing]
{ self: RuntimeException with PropertyResolution[Nothing] =>

	def value = throw this
	def toOption = None
	def map[R](f: Nothing => R): PropertyResolution[R] = this
	def flatMap[R](f: Nothing => PropertyResolution[R]): PropertyResolution[R] = this
	def foreach(f: Nothing => Unit) {}
}
final case class ResolutionException(message: String, exception: Option[Throwable])
	extends RuntimeException(message, exception.getOrElse(null)) with NoPropertyValue
{
	def orElse[R](r: => PropertyResolution[R]) = this
}
final case class UndefinedValue(name: String, environmentLabel: String)
	extends RuntimeException("Value for property '" + name + "' from " + environmentLabel + " is undefined.") with NoPropertyValue
{
	def orElse[R](r: => PropertyResolution[R]) =
		r match
		{
			case u: UndefinedValue => this
			case _ => r
		}
}
final case class DefinedValue[T](value: T, isInherited: Boolean, isDefault: Boolean) extends PropertyResolution[T]
{
	def toOption = Some(value)
	def orElse[R >: T](r: => PropertyResolution[R]) = this
	def map[R](f: T => R) = DefinedValue[R](f(value), isInherited, isDefault)
	def flatMap[R](f: T => PropertyResolution[R]) = f(value)
	def foreach(f: T => Unit) { f(value) }
}