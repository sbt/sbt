/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.scalacheck._
import Prop._
import TaskGen._

object TaskRunnerCircularTest extends Properties("TaskRunner Circular") {
  property("Catches circular references") = forAll(MaxTasksGen, MaxWorkersGen) {
    checkCircularReferences _
  }
  property("Allows references to completed tasks") = forAllNoShrink(MaxTasksGen, MaxWorkersGen) {
    allowedReference _
  }
  final def allowedReference(intermediate: Int, workers: Int) = {
    val top = task(intermediate).named("top")
    def iterate(tk: Task[Int]): Task[Int] =
      tk flatMap { t =>
        if (t <= 0)
          top
        else
          iterate(task(t - 1).named((t - 1).toString))
      }
    try { checkResult(tryRun(iterate(top), true, workers), intermediate) } catch {
      case i: Incomplete if cyclic(i) => ("Unexpected cyclic exception: " + i) |: false
    }
  }
  final def checkCircularReferences(intermediate: Int, workers: Int) = {
    lazy val top = iterate(task(intermediate).named("bottom"), intermediate)
    def iterate(tk: Task[Int], i: Int): Task[Int] =
      tk flatMap { t =>
        if (t <= 0)
          top
        else
          iterate(task(t - 1).named((t - 1).toString), i - 1)
      }
    try { tryRun(top, true, workers); false } catch { case i: Incomplete => cyclic(i) }
  }
  def cyclic(i: Incomplete) =
    Incomplete
      .allExceptions(i)
      .exists(_.isInstanceOf[Execute[({ type A[_] <: AnyRef })#A]#CyclicException[_]])
}
