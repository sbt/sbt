/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.lang.ref.{ ReferenceQueue, WeakReference }
import java.util.concurrent.ConcurrentHashMap

import scala.annotation.tailrec

/**
 * Canonicalizing pool: `intern` returns one instance per distinct value, held weakly.
 *
 * Ported from zinc's `sbt.internal.inc.WeakInterner` to avoid a new dependency, plus `internWith`.
 */
private[librarymanagement] final class WeakInterner[A <: AnyRef] {
  private val stale = new ReferenceQueue[A]
  private val pool = new ConcurrentHashMap[WeakValue[A], WeakValue[A]]

  def intern(a: A): A = internWith(a)(identity)

  /** Like `intern`, but applies `canonicalize` only on a miss, since equality is structural. */
  def internWith(a: A)(canonicalize: A => A): A = {
    expunge()
    lookup(a) match {
      case null => publish(canonicalize(a))
      case hit  => hit
    }
  }

  /** The pooled instance value-equal to `a`, or null if there is none. */
  private def lookup(a: A): A = {
    val probe = new WeakValue(a, stale)
    try
      pool.get(probe) match {
        case null     => null.asInstanceOf[A]
        case existing => existing.get // null if it was collected since it matched
      }
    finally probe.clear() // never enqueue a reference that was not pooled
  }

  private def publish(a: A): A = {
    val candidate = new WeakValue(a, stale)
    @tailrec def attempt(): A = pool.putIfAbsent(candidate, candidate) match {
      case null     => a
      case existing =>
        existing.get match {
          case null => // collected since it matched: drop the dead entry and retry
            pool.remove(existing, existing)
            attempt()
          case canonical =>
            candidate.clear()
            canonical
        }
    }
    attempt()
  }

  @tailrec private def expunge(): Unit = stale.poll() match {
    case null => ()
    case dead =>
      pool.remove(dead, dead)
      expunge()
  }
}

/**
 * Weak reference that hashes and compares by its referent's value.
 *
 * The hash is captured eagerly: it must stay stable after the referent is cleared, or the dead entry
 * could never be found and removed.
 */
private final class WeakValue[A <: AnyRef](a: A, stale: ReferenceQueue[A])
    extends WeakReference[A](a, stale) {
  private val hash: Int = a.hashCode

  override def hashCode(): Int = hash
  override def equals(other: Any): Boolean = other match {
    case that: WeakValue[?] =>
      (this `eq` that) || {
        val value = get
        value != null && value == that.get
      }
    case _ => false
  }
}
