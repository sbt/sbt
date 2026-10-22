/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.util.Try
import hedgehog.*
import hedgehog.runner.*

object ReplaceOnSuccessSpec extends Properties:
  override def tests: List[Test] =
    List(property("close only the resource that the resulting state no longer owns", replace))

  private final class Resource(failClose: Boolean) extends AutoCloseable:
    val closes = new AtomicInteger
    val closeFailure = new IllegalStateException("close failed")
    override def close(): Unit =
      closes.incrementAndGet()
      if failClose then throw closeFailure

  private def replace: Property =
    for
      hasPrevious <- Gen.boolean.forAll
      installs <- Gen.boolean.forAll
      failPreviousClose <- Gen.boolean.forAll
      failReplacementClose <- Gen.boolean.forAll
    yield
      val previous = new Resource(failPreviousClose)
      val replacement = new Resource(failReplacementClose)
      val failure = new RuntimeException("install failed")
      val reported = ListBuffer.empty[Throwable]
      val result = Try(
        BuiltinCommands.replaceOnSuccess(
          Option.when(hasPrevious)(previous),
          replacement,
          reported += _
        ): r =>
          if installs then r else throw failure
      )
      if installs then
        Result.all(
          List(
            Result
              .assert(result.toOption.exists(_ eq replacement))
              .log("returns the install result"),
            previous.closes.get ==== (if hasPrevious then 1 else 0),
            replacement.closes.get ==== 0,
            reported.toList ====
              (if hasPrevious && failPreviousClose then List(previous.closeFailure) else Nil),
          )
        )
      else
        Result.all(
          List(
            Result.assert(result.failed.toOption.exists(_ eq failure)).log("rethrows the failure"),
            previous.closes.get ==== 0,
            replacement.closes.get ==== 1,
            failure.getSuppressed.toList ====
              (if failReplacementClose then List(replacement.closeFailure) else Nil),
            reported.toList ==== Nil,
          )
        )
      end if
end ReplaceOnSuccessSpec
