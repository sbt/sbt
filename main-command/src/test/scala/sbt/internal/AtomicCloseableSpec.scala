/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import verify.BasicTestSuite

object AtomicCloseableSpec extends BasicTestSuite:
  class Probe extends AutoCloseable:
    var closed: Boolean = false
    override def close(): Unit = closed = true
  end Probe

  test("a value that replaces another closes it"):
    val holder = AtomicCloseable[Probe]()
    val first, second = new Probe
    holder.set(first)
    holder.set(second)
    assert(first.closed)
    assert(!second.closed)
    assert(holder.get == second)

  test("closing empties the holder"):
    val holder = AtomicCloseable[Probe]()
    val probe = new Probe
    holder.set(probe)
    holder.close()
    assert(probe.closed)
    assert(holder.get == null)

  test("closing an empty holder does nothing"):
    AtomicCloseable[Probe]().close()

  test("a holder that has a value keeps it"):
    val holder = AtomicCloseable[Probe]()
    val first = new Probe
    holder.set(first)
    val second = new Probe
    assert(holder.setIfEmpty(second) == first)
    assert(!first.closed)
    assert(!second.closed)

  test("a holder that has no value takes the one it is given"):
    val holder = AtomicCloseable[Probe]()
    val probe = new Probe
    assert(holder.setIfEmpty(probe) == probe)
    assert(holder.get == probe)
    assert(!probe.closed)

  test("a value that loses a race is closed"):
    val holder = AtomicCloseable[Probe]()
    val winner, loser = new Probe
    // the holder fills while this caller builds its own value
    val result = holder.setIfEmpty { holder.set(winner); loser }
    assert(result == winner)
    assert(loser.closed)
    assert(!winner.closed)
end AtomicCloseableSpec
