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

object ExecuteSpec extends Properties("Execute") {
  val iGen = Arbitrary.arbInt.arbitrary
  property("evaluates simple task") = forAll(iGen, MaxWorkersGen) { (i: Int, workers: Int) =>
    ("Workers: " + workers) |:
      checkResult(tryRun(task(i), false, workers), i)
  }
  // no direct dependencies currently
  /*property("evaluates simple static graph") = forAll(iGen, MaxWorkersGen) { (i: Int, workers: Int) =>
		("Workers: " + workers) |:
		{
			def result = tryRun(Task(i) dependsOn(task(false),task("a")), false, workers)
			checkResult(result, i)
		}
	}*/

  property("evaluates simple mapped task") = forAll(iGen, MaxTasksGen, MaxWorkersGen) {
    (i: Int, times: Int, workers: Int) =>
      ("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |: {
        def result = tryRun(task(i).map(_ * times), false, workers)
        checkResult(result, i * times)
      }
  }
  property("evaluates chained mapped task") = forAllNoShrink(iGen, MaxTasksGen, MaxWorkersGen) {
    (i: Int, times: Int, workers: Int) =>
      ("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |: {
        val initial = task(0) map (identity[Int])
        def t = (initial /: (0 until times))((t, ignore) => t.map(_ + i))
        checkResult(tryRun(t, false, workers), i * times)
      }
  }

  property("evaluates simple bind") = forAll(iGen, MaxTasksGen, MaxWorkersGen) {
    (i: Int, times: Int, workers: Int) =>
      ("Workers: " + workers) |: ("Value: " + i) |: ("Times: " + times) |: {
        def result = tryRun(task(i).flatMap(x => task(x * times)), false, workers)
        checkResult(result, i * times)
      }
  }
}
