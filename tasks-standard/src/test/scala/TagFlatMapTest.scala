/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import TaskExtra.*
import TaskTest.tryRun
import ConcurrentRestrictions.{ Span, Tag, tagged }

import org.scalacheck.*
import Prop.*

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

/**
 * Verifies that a limit-1 tag restriction is honored for every task in a
 * setup/main/cleanup chain shaped like Tests.testTask: setup and cleanup work
 * forked into individual tasks joined by a nop, the main task reached via
 * dependsOn, and cleanup reached via map/flatMap. Tags apply to one task node
 * only, so each node carrying real work must be tagged individually.
 */
object TagFlatMapTest extends Properties("flatMap tag handling"):
  val testTag = Tag("test")
  val tags = Seq(testTag -> 1)

  def restrictions =
    tagged(m => m.getOrElse(testTag, 0) <= 1 && m.getOrElse(ConcurrentRestrictions.All, 0) <= 8)

  final class Probe:
    val active = new AtomicInteger(0)
    val maxActive = new AtomicInteger(0)
    val events = new ConcurrentLinkedQueue[String]

    def phase(name: String): Unit =
      events.add(s"$name:start")
      val now = active.incrementAndGet()
      maxActive.updateAndGet(m => math.max(m, now))
      Thread.sleep(100)
      active.decrementAndGet()
      events.add(s"$name:end")
      ()

    def eventList: List[String] = events.asScala.toList

  def fj(actions: Seq[() => Unit]): Task[Unit] =
    nop.dependsOn(actions.fork(_()).map(_.tagw(tags*))*)

  def subproject(name: String, probe: Probe): Task[Unit] =
    val setup = fj(Seq(() => probe.phase(s"$name-setup")))
    val main = task(probe.phase(s"$name-main")).tagw(tags*).dependsOn(setup)
    main.map(identity).flatMap { _ =>
      fj(Seq(() => probe.phase(s"$name-cleanup"))).map(_ => ())
    }

  def run2(): Probe =
    val probe = new Probe
    val root = Seq(subproject("a", probe), subproject("b", probe)).join.map(_ => ())
    tryRun(root, true, restrictions)
    probe

  property("tagged tasks stay exclusive through dependsOn/map/flatMap chains") =
    val probe = run2()
    s"maxActive=${probe.maxActive.get} events=${probe.eventList}" |: (probe.maxActive.get == 1)

  /**
   * Pins a current engine limitation: a node's tags are released (and the
   * pending queue drained) between the nodes of a chain, so a tag cannot be
   * held from setup through cleanup and the two subprojects' spans interleave.
   * If this property starts failing, the engine has learned to hold tags
   * across task boundaries and the first property alone is sufficient.
   */
  property("limitation: exclusivity is not held across task boundaries") =
    val probe = run2()
    val ev = probe.eventList
    s"events=$ev" |: !spansDisjoint(ev)

  def spansDisjoint(ev: List[String]): Boolean =
    def span(p: String) = (ev.indexWhere(_.startsWith(p)), ev.lastIndexWhere(_.startsWith(p)))
    val (a0, a1) = span("a-")
    val (b0, b1) = span("b-")
    a1 < b0 || b1 < a0

  def spanSubproject(name: String, probe: Probe): Task[Unit] =
    def fj0(actions: Seq[() => Unit]): Task[Unit] = nop.dependsOn(actions.fork(_())*)
    val setup = fj0(Seq(() => probe.phase(s"$name-setup")))
    val main = task(probe.phase(s"$name-main")).dependsOn(setup)
    val chain = main.flatMap { _ =>
      fj0(Seq(() => probe.phase(s"$name-cleanup"))).map(_ => ())
    }
    nop.tagw((tags :+ (Span -> 1))*).flatMap(_ => chain)

  property("span-tagged bracket keeps subproject spans disjoint") =
    val probe = new Probe
    val root = Seq(spanSubproject("a", probe), spanSubproject("b", probe)).join.map(_ => ())
    tryRun(root, true, restrictions)
    val ev = probe.eventList
    (s"events=$ev" |: spansDisjoint(ev)) &&
    (s"maxActive=${probe.maxActive.get}" |: probe.maxActive.get == 1)
end TagFlatMapTest
