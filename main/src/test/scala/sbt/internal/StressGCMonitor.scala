package sbt.internal

import sbt.internal.util.ConsoleLogger

import scala.concurrent.duration._
import scala.util.Random

object StressGCMonitor {
  var list = List.empty[Int]

  def main(args: Array[String]): Unit = {
    new GCMonitor(ConsoleLogger())
    val deadline = Deadline.now + 10.seconds
    while (!deadline.isOverdue()) {
      println(deadline.timeLeft.toSeconds + " seconds left...")
      list = List.fill(1000 * 1000 * 100)(Random.nextInt(100))
      System.gc()
      Thread.sleep(10)
    }
  }

  def print(): Unit = println(s"random number: ${list.sum}")
}
