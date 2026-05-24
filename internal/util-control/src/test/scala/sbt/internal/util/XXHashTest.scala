/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.nio.ByteBuffer
import verify.BasicTestSuite

object XXHashTest extends BasicTestSuite:
  val hash64: HashAlgo = Hashing.xxhash64
  final val emptyHash = -1205034819632174695L
  final val zeroHash = -1642502924627794072L

  test("Hash empty array"):
    val buf: Array[Byte] = Array[Byte](0)
    val r = hash64.hash(buf, 0, 0, 0)
    assert(r == emptyHash)

  test("Hash empty ByteBuffer"):
    val buf: ByteBuffer = ByteBuffer.allocate(0)
    val r = hash64.hash(buf, 0, 0, 0)
    assert(r == emptyHash)

  test("Hash one byte array"):
    val buf: Array[Byte] = Array[Byte](0)
    val r = hash64.hash(buf, 0, 1, 0)
    assert(r == zeroHash)

  test("Hash one byte ByteBuffer"):
    val buf: ByteBuffer = ByteBuffer.allocate(1)
    buf.put(0: Byte)
    buf.rewind()
    val r = hash64.hash(buf, 0, 1, 0)
    assert(r == zeroHash)

  test("Streaming hash empty ByteBuffer"):
    val hash = Hashing.newStreamingXXHash64(0)
    try
      assert(hash.getValue == emptyHash)
    finally hash.close()

  test("Streaming one byte array"):
    val hash = Hashing.newStreamingXXHash64(0)
    try
      val buf: Array[Byte] = Array[Byte](0)
      hash.update(buf, 0, 1)
      assert(hash.getValue == zeroHash)
    finally hash.close()
end XXHashTest
