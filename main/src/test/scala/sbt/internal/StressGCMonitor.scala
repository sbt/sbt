/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import sbt.internal.util.ConsoleLogger

import scala.concurrent.duration.*
import scala.util.Random

object StressGCMonitor {
  var list = List.empty[Int]

  def main(args: Array[String]): Unit = {
    new GCMonitor(ConsoleLogger())
    val deadline = Deadline.now + 10.seconds
    while (!deadline.isOverdue()) {
      println(s"${deadline.timeLeft.toSeconds} seconds left...")
      list = List.fill(1000 * 1000 * 100)(Random.nextInt(100))
      System.gc()
      Thread.sleep(10)
    }
  }

  def print(): Unit = println(s"random number: ${list.sum}")
}
