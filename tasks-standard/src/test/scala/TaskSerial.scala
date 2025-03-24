/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package std

import sbt.internal.util.Types.*
import TaskExtra.*
import TaskTest.tryRun
import TaskGen.MaxWorkers

import org.scalacheck.*
import Prop.forAll
import Transform.taskToNode
import ConcurrentRestrictions.{ completionService, limitTotal }

import java.util.concurrent.{ CountDownLatch, TimeUnit }

object TaskSerial extends Properties("task serial") {
  val checkCycles = true
  val Timeout = 100L // in milliseconds

  def eval[T](t: Task[T]): T = tryRun(t, checkCycles, limitTotal(MaxWorkers))

  property("Evaluates basic") = forAll { (i: Int) =>
    checkResult(eval(task(i)), i)
  }

  property("Evaluates Function0") = forAll { (i: Int) =>
    checkResult(eval(() => i), i)
  }

  // Note - This test is flaky on cpu/mem pressured machines, so we ignore it for now.
  // Any test using a timeout (especially 100ms) on EC2 is guaranteed flaky.
  /*
  // verifies that all tasks get scheduled simultaneously (1-3) or do not (4)
  property("Allows arbitrary task limit") = forAll(MaxWorkersGen) { (sze: Int) =>
    val size = math.max(1, sze)
    val halfSize = size / 2 + 1
    val all =
      checkArbitrary(size, tagged(_ => true), true) &&
        checkArbitrary(size, unrestricted[Task[_]], true) &&
        checkArbitrary(size, limitTotal[Task[_]](size), true) &&
        checkArbitrary(size, limitTotal[Task[_]](halfSize), size <= halfSize)
    all :| ("Size: " + size) :| ("Half size: " + halfSize)
  }
   */

  def checkArbitrary(
      size: Int,
      restrictions: ConcurrentRestrictions,
      shouldSucceed: Boolean
  ) = {
    val latch = task { new CountDownLatch(size) }
    def mktask = latch map { l =>
      l.countDown()
      l.await(Timeout, TimeUnit.MILLISECONDS)
    }
    val tasks = (0 until size).map(_ => mktask).toList.join.map { results =>
      val success = results.forall(idFun[Boolean])
      assert(success == shouldSucceed, if (shouldSucceed) unschedulableMsg else scheduledMsg)
    }
    checkResult(evalRestricted(tasks)(restrictions), ())
  }
  def unschedulableMsg =
    "Some tasks were unschedulable: verify this is an actual failure by extending the timeout to several seconds."
  def scheduledMsg = "All tasks were unexpectedly scheduled."

  def evalRestricted[T](t: Task[T])(restrictions: ConcurrentRestrictions): T =
    tryRun[T](t, checkCycles, restrictions)
}

object TaskTest {
  def run[T](
      root: Task[T],
      checkCycles: Boolean,
      restrictions: ConcurrentRestrictions
  ): Result[T] = {
    val (service, shutdown) =
      completionService(restrictions, (x: String) => System.err.println(x))

    val x = new Execute(
      Execute.config(checkCycles),
      Execute.noTriggers,
      ExecuteProgress.empty
    )(using taskToNode([A] => (id: TaskId[A]) => id.asInstanceOf))
    try {
      x.run(root)(using service)
    } finally {
      shutdown()
    }
  }
  def tryRun[T](
      root: Task[T],
      checkCycles: Boolean,
      restrictions: ConcurrentRestrictions
  ): T =
    run(root, checkCycles, restrictions) match {
      case Result.Value(v) => v
      case Result.Inc(i)   => throw i
    }
}
