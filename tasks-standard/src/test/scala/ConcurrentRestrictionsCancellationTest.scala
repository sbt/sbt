/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.std

import java.util.concurrent.{ CancellationException, CountDownLatch, Executors, TimeUnit }
import hedgehog.Result
import hedgehog.runner._
import sbt.{ ConcurrentRestrictions, Incomplete }
import scala.util.{ Failure, Try }

object ConcurrentRestrictionsCancellationTest extends Properties {
  override def tests: List[Test] = List(
    example("a cancelled sentinel is taken as an Incomplete", cancelledSentinel),
  )

  private def cancelledSentinel: Result = {
    val pool = Executors.newCachedThreadPool()
    val started = new CountDownLatch(1)
    val service = ConcurrentRestrictions.completionService[String, Unit](
      pool,
      ConcurrentRestrictions.unrestricted[String],
      (_: String) => (),
      (node: String) => node == "sentinel"
    )
    try {
      service.submit("sentinel", () => {
        started.countDown()
        Thread.sleep(60000)
      })
      Result
        .assert(started.await(10, TimeUnit.SECONDS))
        .log("the sentinel did not start")
        .and {
          service.cancelSentinels()
          Try(service.take()) match {
            case Failure(i: Incomplete) =>
              Result
                .assert(i.message.contains("cancelled"))
                .and(Result.assert(i.directCause.exists(_.isInstanceOf[CancellationException])))
                .log(s"got $i")
            case other => Result.failure.log(s"expected an Incomplete, got $other")
          }
        }
    } finally {
      service.close()
      pool.shutdownNow()
      ()
    }
  }
}
