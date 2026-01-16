/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.Util.*
import sbt.librarymanagement.Configuration

enum ScopeAxis[+A1]:
  import Scope.RefThenConfig

  /**
   * Select is a type constructor that is used to wrap type `S`
   * to make a scope component, equivalent of Some in Option.
   */
  case Select(axis: A1) extends ScopeAxis[A1]

  /**
   * This is a scope component that represents not being
   * scoped by the user, which later could be further scoped automatically
   * by sbt.
   */
  case This extends ScopeAxis[Nothing]

  /**
   * Zero is a scope component that represents not scoping.
   * It is a universal fallback component that is strictly weaker
   * than any other values on a scope axis.
   */
  case Zero extends ScopeAxis[Nothing]

  def isSelect: Boolean = this match
    case Select(_) => true
    case _         => false

  def foldStrict[A2](f: A1 => A2, ifZero: A2, ifThis: A2): A2 = fold(f, ifZero, ifThis)

  def fold[A2](f: A1 => A2, ifZero: => A2, ifThis: => A2): A2 = this match
    case This      => ifThis
    case Zero      => ifZero
    case Select(s) => f(s)

  def toOption: Option[A1] = foldStrict(Option(_), none, none)

  def map[A2](f: A1 => A2): ScopeAxis[A2] =
    foldStrict(s => Select(f(s)): ScopeAxis[A2], Zero: ScopeAxis[A2], This: ScopeAxis[A2])

  def asScope(using A1 <:< Reference): Scope =
    Scope(this.asInstanceOf[ScopeAxis[Reference]], This, This, This)

  inline def /[K](key: Scoped.ScopingSetting[K])(using A1 <:< Reference): K = key.rescope(asScope)

  inline def /(c: ConfigKey)(using A1 <:< Reference): RefThenConfig =
    RefThenConfig(this.asInstanceOf, c)
  inline def /(c: Configuration)(using A1 <:< Reference): RefThenConfig =
    RefThenConfig(this.asInstanceOf, c: ConfigKey)
  // This is for handling `Zero / Zero / name`.
  inline def /(configAxis: ScopeAxis[ConfigKey])(using A1 <:< Reference): RefThenConfig =
    RefThenConfig(this.asInstanceOf, configAxis)
end ScopeAxis

object ScopeAxis:
  def `this`[A1]: ScopeAxis[A1] = ScopeAxis.This
  def zero[A1]: ScopeAxis[A1] = ScopeAxis.Zero

  def fromOption[A1](o: Option[A1]): ScopeAxis[A1] = o match
    case Some(v) => ScopeAxis.Select(v)
    case None    => ScopeAxis.Zero
end ScopeAxis
