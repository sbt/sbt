package sbt

import Types.some

sealed trait ScopeAxis[+S] {
  def foldStrict[T](f: S => T, ifGlobal: T, ifThis: T): T = fold(f, ifGlobal, ifThis)
  def fold[T](f: S => T, ifGlobal: => T, ifThis: => T): T = this match {
    case This      => ifThis
    case Global    => ifGlobal
    case Select(s) => f(s)
  }
  def toOption: Option[S] = foldStrict(some.fn, None, None)
  def map[T](f: S => T): ScopeAxis[T] = foldStrict(s => Select(f(s)), Global, This)
  def isSelect: Boolean = false
}
case object This extends ScopeAxis[Nothing]
case object Global extends ScopeAxis[Nothing]
final case class Select[S](s: S) extends ScopeAxis[S] {
  override def isSelect = true
}
object ScopeAxis {
  implicit def scopeAxisToScope(axis: ScopeAxis[Nothing]): Scope =
    Scope(axis, axis, axis, axis)

  implicit def referenceToScopeAxis(p: Reference): ScopeAxis[Reference] =
    Select(p)

  // This is for handling `key in (Zero, Compile)`
  implicit def configurationToScopeAxis(c: Configuration): ScopeAxis[ConfigKey] =
    Select(ConfigKey(c.name))

  implicit def configKeyToScopeAxis(c: ConfigKey): ScopeAxis[ConfigKey] =
    Select(c)

  // This is for handling `key in (Zero, Zero, console)`
  implicit def scopedToScopeAxis(t: Scoped): ScopeAxis[AttributeKey[_]] =
    Select(t.key)

  def fromOption[T](o: Option[T]): ScopeAxis[T] = o match {
    case Some(v) => Select(v)
    case None    => Global
  }
}
