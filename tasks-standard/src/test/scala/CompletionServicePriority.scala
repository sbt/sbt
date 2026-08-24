/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch, TimeUnit }

import org.scalacheck.*
import Prop.*

import scala.jdk.CollectionConverters.*

object CompletionServicePrioritySpec extends Properties("CompletionServicePriority") {

  private val Timeout = 60

  // A single slot, so every task after the first is held back and the order the service lets them
  // out in is the only thing the property can be measuring.
  private def oneSlot = ConcurrentRestrictions.tagged { m =>
    m.getOrElse(ConcurrentRestrictions.All, 0) <= 1
  }

  property("the next free slot goes to the task with the lowest priority number") = {
    val (service, shutdown) = ConcurrentRestrictions.completionService(oneSlot, _ => ())
    try {
      val started = new ConcurrentLinkedQueue[String]
      val occupied = new CountDownLatch(1)
      val release = new CountDownLatch(1)

      def submit(name: String, priority: Int)(body: => Unit): Unit =
        service.submit(
          std.TaskExtra.task(()).withPriority(priority),
          () => {
            started.add(name)
            body
            Execute.completed(())
          }
        )

      submit("occupier", 0) {
        occupied.countDown()
        release.await(Timeout.toLong, TimeUnit.SECONDS)
        ()
      }
      occupied.await(Timeout.toLong, TimeUnit.SECONDS)

      // Submitted worst first, so plain submission order would run them in exactly this order.
      submit("second-jvm", 2)(())
      submit("first-jvm", -1)(())
      release.countDown()

      (1 to 3).foreach(_ => service.take().process())
      val order = started.asScala.toList
      s"ran in order $order" |: (order == List("occupier", "first-jvm", "second-jvm"))
    } finally shutdown()
  }

  property("tasks of equal priority keep the order they were held back in") = {
    val (service, shutdown) = ConcurrentRestrictions.completionService(oneSlot, _ => ())
    try {
      val started = new ConcurrentLinkedQueue[String]
      val occupied = new CountDownLatch(1)
      val release = new CountDownLatch(1)

      def submit(name: String)(body: => Unit): Unit =
        service.submit(
          std.TaskExtra.task(()),
          () => {
            started.add(name)
            body
            Execute.completed(())
          }
        )

      submit("occupier") {
        occupied.countDown()
        release.await(Timeout.toLong, TimeUnit.SECONDS)
        ()
      }
      occupied.await(Timeout.toLong, TimeUnit.SECONDS)

      val rest = (1 to 5).map(i => s"task-$i")
      rest.foreach(name => submit(name)(()))
      release.countDown()

      (0 to rest.size).foreach(_ => service.take().process())
      val order = started.asScala.toList
      s"ran in order $order" |: (order == "occupier" :: rest.toList)
    } finally shutdown()
  }
}
