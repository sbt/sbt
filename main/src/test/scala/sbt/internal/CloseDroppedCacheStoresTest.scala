/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.util.{ ActionCacheStore, InMemoryActionCacheStore, ReadOnlyCacheStore }

object CloseDroppedCacheStoresTest extends Properties:
  class ClosingStore extends InMemoryActionCacheStore, AutoCloseable:
    var closed = 0
    override def close(): Unit = closed += 1

  override def tests: List[Test] = List(
    example("same store is kept", check(s => Seq(s), s => Seq(s), 0)),
    example(
      "read-only wrappers of the same store are kept",
      check(s => Seq(ReadOnlyCacheStore(s)), s => Seq(ReadOnlyCacheStore(s)), 0)
    ),
    example("wrapping the store keeps it", check(s => Seq(s), s => Seq(ReadOnlyCacheStore(s)), 0)),
    example(
      "unwrapping the store keeps it",
      check(s => Seq(ReadOnlyCacheStore(s)), s => Seq(s), 0)
    ),
    example(
      "dropped wrapper closes the store once",
      check(s => Seq(s, ReadOnlyCacheStore(s)), _ => Nil, 1)
    ),
  )

  def check(
      before: ClosingStore => Seq[ActionCacheStore],
      after: ClosingStore => Seq[ActionCacheStore],
      expectedCloses: Int
  ): Result =
    val store = ClosingStore()
    Compiler.closeDroppedCacheStores(before(store), after(store))
    Result.assert(store.closed == expectedCloses)
end CloseDroppedCacheStoresTest
