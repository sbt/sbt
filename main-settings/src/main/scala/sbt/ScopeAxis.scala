/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

sealed trait ScopeAxis[+S] {
  def foldStrict[T](f: S => T, ifZero: T, ifThis: T): T = fold(f, ifZero, ifThis)
  def fold[T](f: S => T, ifZero: => T, ifThis: => T): T = this match {
    case This      => ifThis
    case Zero      => ifZero
    case Select(s) => f(s)
  }
  def toOption: Option[S] = foldStrict(Option(_), None, None)
  def map[T](f: S => T): ScopeAxis[T] =
    foldStrict(s => Select(f(s)): ScopeAxis[T], Zero: ScopeAxis[T], This: ScopeAxis[T])
  def isSelect: Boolean = false
}

/**
 * This is a scope component that represents not being
 * scoped by the user, which later could be further scoped automatically
 * by sbt.
 */
case object This extends ScopeAxis[Nothing]

/**
 * Zero is a scope component that represents not scoping.
 * It is a universal fallback component that is strictly weaker
 * than any other values on a scope axis.
 */
case object Zero extends ScopeAxis[Nothing]

/**
 * Select is a type constructor that is used to wrap type `S`
 * to make a scope component, equivalent of Some in Option.
 */
final case class Select[S](s: S) extends ScopeAxis[S] {
  override def isSelect = true
}
object ScopeAxis {
  def fromOption[T](o: Option[T]): ScopeAxis[T] = o match {
    case Some(v) => Select(v)
    case None    => Zero
  }
}
