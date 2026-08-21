/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.io.IO
import _root_.sbt.util.CacheStore
import sjsonnew.BasicJsonProtocol.given
import java.nio.file.{ Files, Path }

object InMemoryCacheStoreTest extends Properties:
  override def tests: List[Test] = List(
    example("a read populates the cache", readPopulates),
    example("a write populates the cache", writePopulates),
    example("a newer file invalidates the cached value", newerFileInvalidates),
    example("a read of a store with no file still reports absent", missingFileIsAbsent),
  )

  private def withStore[A](budget: Long = 1024L * 1024L)(f: (Path, CacheStore) => A): A =
    val dir = Files.createTempDirectory("sbt-inmemory-cache")
    val factoryFactory = InMemoryCacheStore.factory(budget)
    try f(dir.resolve("value"), factoryFactory(dir).make("value"))
    finally factoryFactory.close()

  /**
   * Rewrites the file's bytes while restoring its modification time, so only an in-memory entry can still
   * answer with the original value. `write` would move the timestamp, so the replacement goes in plain.
   */
  private def overwriteKeepingTimestamp(path: Path, value: String): Unit =
    val stamp = IO.getModifiedTimeOrZero(path.toFile)
    CacheStore.file(path.toFile).write(value)
    val _ = IO.setModifiedTimeOrFalse(path.toFile, stamp)

  def readPopulates: Result =
    withStore() { (path, store) =>
      CacheStore.file(path.toFile).write("first")
      val readFromDisk = store.read[String]()
      overwriteKeepingTimestamp(path, "second")
      val readFromCache = store.read[String]()
      Result.assert(readFromDisk == "first").log(s"disk read gave $readFromDisk") and
        Result
          .assert(readFromCache == "first")
          .log(s"expected the cached 'first', got '$readFromCache' -- the read did not populate")
    }

  def writePopulates: Result =
    withStore() { (path, store) =>
      store.write("written")
      overwriteKeepingTimestamp(path, "clobbered")
      val value = store.read[String]()
      Result.assert(value == "written").log(s"expected 'written', got '$value'")
    }

  def newerFileInvalidates: Result =
    withStore() { (path, store) =>
      store.write("old")
      CacheStore.file(path.toFile).write("new")
      val _ = IO.setModifiedTimeOrFalse(path.toFile, IO.getModifiedTimeOrZero(path.toFile) + 5000L)
      val value = store.read[String]()
      Result.assert(value == "new").log(s"a moved timestamp must re-read from disk, got '$value'")
    }

  def missingFileIsAbsent: Result =
    withStore() { (_, store) =>
      Result.assert(store.read[String]("fallback") == "fallback")
    }
