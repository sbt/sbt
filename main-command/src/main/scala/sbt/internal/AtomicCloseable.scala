/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.concurrent.atomic.AtomicReference

import sbt.internal.util.Util

/** Holds a closeable that a caller replaces, and closes the one it replaces. */
private[sbt] class AtomicCloseable[A >: Null <: AutoCloseable](val ref: AtomicReference[A])
    extends AnyVal:
  def get: A = ref.get
  def set(c: A): Unit = AtomicCloseable.close(ref.getAndSet(c))
  def close(): Unit = AtomicCloseable.close(ref.getAndSet(null))

  /** Keeps the value another caller put here, and closes the one this caller built. */
  def setIfEmpty(ctor: => A): A =
    var obj = ref.get
    if obj eq null then
      val value = ctor
      require(value ne null, "AtomicCloseable.setIfEmpty: `ctor` must not return null")
      while obj eq null do obj = if ref.compareAndSet(null, value) then value else ref.get
      if obj ne value then AtomicCloseable.close(value)
    obj
end AtomicCloseable

private[sbt] object AtomicCloseable:
  def apply[A >: Null <: AutoCloseable](): AtomicCloseable[A] =
    new AtomicCloseable(new AtomicReference[A])

  def close(obj: AutoCloseable): Unit =
    if obj ne null then Util.ignoreTry(obj.close())
end AtomicCloseable
