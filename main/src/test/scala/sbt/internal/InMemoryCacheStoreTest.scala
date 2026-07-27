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
    example("makeCompressed reaches the delegate", compressedReachesDelegate),
    example("a store that did not write the file reads it back", freshStoreReadsFromDisk),
  )

  /** Two factories over one directory, each with its own cache, as two sessions would be. */
  private def withSessions[A](f: (Path, String => CacheStore, String => CacheStore) => A): A =
    val dir = Files.createTempDirectory("sbt-inmemory-cache")
    val first = InMemoryCacheStore.factory(1024L * 1024L)
    val second = InMemoryCacheStore.factory(1024L * 1024L)
    try f(dir, first(dir).makeCompressed, second(dir).makeCompressed)
    finally
      first.close()
      second.close()

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

  def compressedReachesDelegate: Result =
    withSessions { (dir, first, _) =>
      first("value").write("a highly repetitive payload")
      val magic = IO.readBytes(dir.resolve("value").toFile).take(2)
      Result
        .assert(magic.length == 2 && magic(0) == 0x1f.toByte && magic(1) == 0x8b.toByte)
        .log("the factory must forward makeCompressed, not wrap a plain delegate")
    }

  def freshStoreReadsFromDisk: Result =
    withSessions { (_, first, second) =>
      first("value").write("written by the first session")
      val value = second("value").read[String]()
      Result.assert(value == "written by the first session").log(s"got '$value'")
    }
