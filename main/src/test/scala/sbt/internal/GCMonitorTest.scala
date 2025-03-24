/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.atomic.AtomicReference

import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*

class GCMonitorTest extends AnyFunSuite {
  class TestMonitor extends GCMonitorBase {
    val loggedTotals = ListBuffer.empty[Long]
    override protected val window = 10.seconds
    override protected val ratio = 0.5

    override protected def emitWarning(total: Long, over: Option[Long]): Unit =
      loggedTotals += total
  }

  val collectionTimeStore = new AtomicReference(0.millis -> 0L)

  def simulateBy(f: Long => Long): Long => List[Long] = { duration =>
    collectionTimeStore.set(0.millis -> 0)
    val testMonitor = new TestMonitor
    for (x <- 0L to duration by 100)
      testMonitor.totalCollectionTimeChanged(x, f(x), collectionTimeStore)
    testMonitor.loggedTotals.toList
  }

  test("GC time = time") {
    val simulate = simulateBy(identity)
    assertResult(List())(simulate(5000))
    assertResult(List(5100))(simulate(10000))
    assertResult(List(5100, 10000))(simulate(20000))
    assertResult(List(5100, 10000, 10000))(simulate(30000))
    assertResult(List(5100, 10000, 10000, 10000))(simulate(40000))
  }

  test("GC time = 0.5 * time") {
    val simulate = simulateBy(_ / 2)
    assertResult(List())(simulate(10000))
    assertResult(List())(simulate(20000))
    assertResult(List())(simulate(30000))
    assertResult(List())(simulate(40000))
  }

  test("GC time = 2 * time") {
    val simulate = simulateBy(_ * 2)
    assertResult(List(5200))(simulate(5000))
    assertResult(List(5200))(simulate(10000))
    assertResult(List(5200, 20000))(simulate(20000))
    assertResult(List(5200, 20000, 20000))(simulate(30000))
    assertResult(List(5200, 20000, 20000, 20000))(simulate(40000))
  }
}
