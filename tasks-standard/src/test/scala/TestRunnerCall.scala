/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

import sbt._

import org.scalacheck._
import Prop._
import TaskGen._

object TaskRunnerCallTest extends Properties("TaskRunner Call") {
  property("calculates fibonacci") = forAll(MaxTasksGen, MaxWorkersGen) { (i: Int, workers: Int) =>
    (i > 0) ==> {
      val f = fibDirect(i)
      ("Workers: " + workers) |: ("i: " + i) |: ("fib(i): " + f) |: {
        def result = tryRun(fibTask(i), false, workers)
        checkResult(result, f)
      }
    }
  }
  final def fibTask(i: Int) = {
    require(i > 0)
    lazy val next: (Int, Int, Int) => Task[Int] =
      (index, x1, x2) => {
        if (index == i)
          task(x2)
        else
          iterate((index + 1, x2, x1 + x2))
      }
    def iterate(iteration: (Int, Int, Int)) = task(iteration) flatMap next.tupled
    iterate((1, 0, 1))
  }
  final def fibDirect(i: Int): Int = {
    require(i > 0)
    def build(index: Int, x1: Int, x2: Int): Int =
      if (index == i)
        x2
      else
        build(index + 1, x2, x1 + x2)
    build(1, 0, 1)
  }
}
