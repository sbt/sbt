/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalacheck.*
import Gen.choose

object TaskGen extends std.TaskExtra {
  // upper bounds to make the tests finish in reasonable time
  val MaxTasks = 100
  val MaxWorkers = 29
  val MaxJoin = 20

  val MaxTasksGen = choose(0, MaxTasks)
  val MaxWorkersGen = choose(1, MaxWorkers)
  val MaxJoinGen = choose(0, MaxJoin)
  val TaskListGen = MaxTasksGen.flatMap(size => Gen.listOfN(size, Arbitrary.arbInt.arbitrary))

  def run[T](root: Task[T], checkCycles: Boolean, maxWorkers: Int): Result[T] = {
    val (service, shutdown) = CompletionService(maxWorkers)
    val dummies = std.Transform.DummyTaskMap(Nil)
    val x = new Execute(
      Execute.config(checkCycles),
      Execute.noTriggers,
      ExecuteProgress.empty
    )(using std.Transform(dummies))
    try {
      x.run(root)(using service)
    } finally {
      shutdown()
    }
  }
  def tryRun[T](root: Task[T], checkCycles: Boolean, maxWorkers: Int): T =
    run(root, checkCycles, maxWorkers) match {
      case Result.Value(v) => v
      case Result.Inc(i)   => throw i
    }
}
