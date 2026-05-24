/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.lang.Long.rotateLeft
import SafeUtils.checkRange
import VarHandleUtils.*
import XXHashConstants.*

/**
 * The implementation is based on lz4-java.
 * Copyright 2020 Linnaea Von Lavia and the lz4-java contributors.
 * Licensed under the Apache License.
 *
 * Streaming xxhash.
 */
class StreamingXXHash64VarHandle(seed: Long) extends AbstractStreamingXXHash64Scala(seed):

  override def getValue: Long =
    var h64: Long = 0L
    if totalLen >= 32 then
      var v1: Long = this.v1
      var v2: Long = this.v2
      var v3: Long = this.v3
      var v4: Long = this.v4

      h64 = rotateLeft(v1, 1) + rotateLeft(v2, 7) + rotateLeft(v3, 12) + rotateLeft(v4, 18);

      v1 *= PRIME64_2
      v1 = rotateLeft(v1, 31)
      v1 *= PRIME64_1; h64 ^= v1
      h64 = h64 * PRIME64_1 + PRIME64_4

      v2 *= PRIME64_2
      v2 = rotateLeft(v2, 31)
      v2 *= PRIME64_1
      h64 ^= v2
      h64 = h64 * PRIME64_1 + PRIME64_4;

      v3 *= PRIME64_2
      v3 = rotateLeft(v3, 31)
      v3 *= PRIME64_1
      h64 ^= v3
      h64 = h64 * PRIME64_1 + PRIME64_4

      v4 *= PRIME64_2
      v4 = rotateLeft(v4, 31)
      v4 *= PRIME64_1
      h64 ^= v4
      h64 = h64 * PRIME64_1 + PRIME64_4
    else h64 = seed + PRIME64_5

    h64 += totalLen

    var off: Int = 0
    while off <= memSize - 8 do
      var k1: Long = readLongLE(memory, off)
      k1 *= PRIME64_2
      k1 = rotateLeft(k1, 31)
      k1 *= PRIME64_1
      h64 ^= k1
      h64 = rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4
      off += 8

    if off <= memSize - 4 then
      h64 ^= (readIntLE(memory, off) & 0xffffffffL) * PRIME64_1
      h64 = rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3
      off += 4
    else ()

    while off < memSize do
      h64 ^= (memory(off) & 0xff) * PRIME64_5
      h64 = rotateLeft(h64, 11) * PRIME64_1
      off += 1

    h64 ^= h64 >>> 33
    h64 *= PRIME64_2
    h64 ^= h64 >>> 29
    h64 *= PRIME64_3
    h64 ^= h64 >>> 32

    h64
  end getValue

  override def update(buf: Array[Byte], offset: Int, len: Int): Unit =
    var off = offset
    checkRange(buf, off, len)

    totalLen += len

    if memSize + len < 32 then // fill in tmp buffer
      System.arraycopy(buf, off, memory, memSize, len)
      memSize += len
    else
      val end: Int = off + len

      if memSize > 0 then // data left from previous update
        System.arraycopy(buf, off, memory, memSize, 32 - memSize)

        v1 += readLongLE(memory, 0) * PRIME64_2
        v1 = rotateLeft(v1, 31)
        v1 *= PRIME64_1

        v2 += readLongLE(memory, 8) * PRIME64_2
        v2 = rotateLeft(v2, 31)
        v2 *= PRIME64_1

        v3 += readLongLE(memory, 16) * PRIME64_2
        v3 = rotateLeft(v3, 31)
        v3 *= PRIME64_1

        v4 += readLongLE(memory, 24) * PRIME64_2
        v4 = rotateLeft(v4, 31)
        v4 *= PRIME64_1

        off += 32 - memSize
        memSize = 0
      else ()

      {
        val limit: Int = end - 32
        var v1: Long = this.v1
        var v2: Long = this.v2
        var v3: Long = this.v3
        var v4: Long = this.v4

        while off <= limit do
          v1 += readLongLE(buf, off) * PRIME64_2
          v1 = rotateLeft(v1, 31)
          v1 *= PRIME64_1
          off += 8

          v2 += readLongLE(buf, off) * PRIME64_2
          v2 = rotateLeft(v2, 31)
          v2 *= PRIME64_1
          off += 8

          v3 += readLongLE(buf, off) * PRIME64_2
          v3 = rotateLeft(v3, 31)
          v3 *= PRIME64_1
          off += 8

          v4 += readLongLE(buf, off) * PRIME64_2
          v4 = rotateLeft(v4, 31)
          v4 *= PRIME64_1
          off += 8

        this.v1 = v1
        this.v2 = v2
        this.v3 = v3
        this.v4 = v4
      }

      if off < end then
        System.arraycopy(buf, off, memory, 0, end - off)
        memSize = end - off
      else ()
    end if
  end update

end StreamingXXHash64VarHandle
