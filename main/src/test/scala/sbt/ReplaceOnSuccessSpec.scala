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
import hedgehog._
import hedgehog.runner._

object ReplaceOnSuccessSpec extends Properties {
  override def tests: List[Test] =
    List(property("close only the resource that the resulting state no longer owns", replace))

  private final class Resource(failClose: Boolean) extends AutoCloseable {
    val closes = new AtomicInteger
    val closeFailure = new IllegalStateException("close failed")
    override def close(): Unit = {
      closes.incrementAndGet()
      if (failClose) throw closeFailure
    }
  }

  private def replace: Property =
    for {
      hasPrevious <- Gen.boolean.forAll
      installs <- Gen.boolean.forAll
      failPreviousClose <- Gen.boolean.forAll
      failReplacementClose <- Gen.boolean.forAll
    } yield {
      val previous = new Resource(failPreviousClose)
      val replacement = new Resource(failReplacementClose)
      val failure = new RuntimeException("install failed")
      val reported = ListBuffer.empty[Throwable]
      val result = Try(
        BuiltinCommands.replaceOnSuccess(
          if (hasPrevious) Some(previous) else None,
          replacement,
          e => { reported += e; () }
        ) { r =>
          if (installs) r else throw failure
        }
      )
      if (installs)
        hedgehog.Result.all(
          List(
            hedgehog.Result
              .assert(result.toOption.exists(_ eq replacement))
              .log("returns the install result"),
            previous.closes.get ==== (if (hasPrevious) 1 else 0),
            replacement.closes.get ==== 0,
            reported.toList ====
              (if (hasPrevious && failPreviousClose) List(previous.closeFailure) else Nil),
          )
        )
      else
        hedgehog.Result.all(
          List(
            hedgehog.Result
              .assert(result.failed.toOption.exists(_ eq failure))
              .log("rethrows the failure"),
            previous.closes.get ==== 0,
            replacement.closes.get ==== 1,
            failure.getSuppressed.toList ====
              (if (failReplacementClose) List(replacement.closeFailure) else Nil),
            reported.toList ==== Nil,
          )
        )
    }
}
