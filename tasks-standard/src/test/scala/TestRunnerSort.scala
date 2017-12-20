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

object TaskRunnerSortTest extends Properties("TaskRunnerSort") {
  property("sort") = forAll(TaskListGen, MaxWorkersGen) { (list: List[Int], workers: Int) =>
    val a = list.toArray
    val sorted = a.toArray
    java.util.Arrays.sort(sorted)
    ("Workers: " + workers) |: ("Array: " + a.toList) |: {
      def result = tryRun(sort(a.toSeq), false, if (workers > 0) workers else 1)
      checkResult(result.toList, sorted.toList)
    }
  }
  final def sortDirect(a: Seq[Int]): Seq[Int] = {
    if (a.length < 2)
      a
    else {
      val pivot = a(0)
      val (lt, gte) = a.view.drop(1).partition(_ < pivot)
      sortDirect(lt) ++ List(pivot) ++ sortDirect(gte)
    }
  }
  final def sort(a: Seq[Int]): Task[Seq[Int]] = {
    if (a.length < 200)
      task(sortDirect(a))
    else {
      task(a) flatMap { a =>
        val pivot = a(0)
        val (lt, gte) = a.view.drop(1).partition(_ < pivot)
        Test.t2(sort(lt), sort(gte)) map {
          case (l, g) => l ++ List(pivot) ++ g
        }
      }
    }
  }
}
