/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.File
import sbt.io.IO
import sjsonnew.BasicJsonProtocol.*

object GzipCacheStoreSpec extends verify.BasicTestSuite:

  test("a gzip store round trips"):
    IO.withTemporaryDirectory: dir =>
      val store = CacheStore.gzipFile(new File(dir, "cache.bin"))
      store.write(Vector("alpha", "beta", "gamma"))
      val got: Vector[String] = store.read[Vector[String]]()
      assert(got == Vector("alpha", "beta", "gamma"))

  test("a gzip store writes gzip framing"):
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "cache.bin")
      CacheStore.gzipFile(file).write(Vector.fill(200)("a highly repetitive payload"))
      val magic = IO.readBytes(file).take(2)
      assert(magic(0) == 0x1f.toByte && magic(1) == 0x8b.toByte, "expected gzip magic bytes")

  test("a gzip store compresses repetitive content"):
    IO.withTemporaryDirectory: dir =>
      val payload = Vector.fill(2000)("a highly repetitive payload")
      val plain = new File(dir, "plain.bin")
      val gzipped = new File(dir, "gzipped.bin")
      CacheStore.file(plain).write(payload)
      CacheStore.gzipFile(gzipped).write(payload)
      assert(
        gzipped.length * 10 < plain.length,
        s"expected >10x, got ${plain.length} -> ${gzipped.length}"
      )

  test("a gzip store reads a plain uncompressed cache"):
    IO.withTemporaryDirectory: dir =>
      val file = new File(dir, "cache.bin")
      CacheStore.file(file).write(Vector("written", "uncompressed"))
      val got: Vector[String] = CacheStore.gzipFile(file).read[Vector[String]]()
      assert(got == Vector("written", "uncompressed"))

  test("a gzip store overwrites rather than appends"):
    IO.withTemporaryDirectory: dir =>
      val store = CacheStore.gzipFile(new File(dir, "cache.bin"))
      store.write(Vector("first"))
      store.write(Vector("second"))
      val got: Vector[String] = store.read[Vector[String]]()
      assert(got == Vector("second"))

  test("makeCompressed produces a compressed store"):
    IO.withTemporaryDirectory: dir =>
      val store = CacheStoreFactory.directory(dir).makeCompressed("output")
      store.write(Vector("via", "the", "factory"))
      val got: Vector[String] = store.read[Vector[String]]()
      assert(got == Vector("via", "the", "factory"))
      val magic = IO.readBytes(new File(dir, "output")).take(2)
      assert(magic(0) == 0x1f.toByte && magic(1) == 0x8b.toByte)
