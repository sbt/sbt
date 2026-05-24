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
import java.nio.ByteBuffer
import VarHandleUtils.*
import XXHashConstants.*

object XXHash64VarHandle:
  private[sbt] val INSTANCE = new XXHash64VarHandle()
end XXHash64VarHandle

/**
 * The implementation is based on lz4-java.
 * Copyright 2020 Linnaea Von Lavia and the lz4-java contributors.
 * Licensed under the Apache License.
 *
 * Instances of this class are **not** thread-safe.
 */
class XXHash64VarHandle extends HashAlgo:
  override def hash(buf: Array[Byte], offset: Int, len: Int, seed: Long): Long =
    SafeUtils.checkRange(buf, offset, len)

    var off = offset
    val end: Int = off + len
    var h64: Long = 0L

    if len >= 32 then
      val limit = end - 32
      var v1: Long = seed + PRIME64_1 + PRIME64_2
      var v2: Long = seed + PRIME64_2
      var v3: Long = seed + 0
      var v4: Long = seed - PRIME64_1
      while
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
        v4 = v4 * PRIME64_1
        off += 8
        off <= limit
      do ()

      h64 = rotateLeft(v1, 1) + rotateLeft(v2, 7) + rotateLeft(v3, 12) + rotateLeft(v4, 18)

      v1 *= PRIME64_2
      v1 = rotateLeft(v1, 31)
      v1 *= PRIME64_1
      h64 ^= v1
      h64 = h64 * PRIME64_1 + PRIME64_4

      v2 *= PRIME64_2
      v2 = rotateLeft(v2, 31)
      v2 *= PRIME64_1
      h64 ^= v2
      h64 = h64 * PRIME64_1 + PRIME64_4

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

    h64 += len

    while off <= end - 8 do
      var k1: Long = readLongLE(buf, off)
      k1 *= PRIME64_2
      k1 = rotateLeft(k1, 31)
      k1 *= PRIME64_1
      h64 ^= k1
      h64 = rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4
      off += 8

    if off <= end - 4 then
      h64 ^= (readIntLE(buf, off) & 0xffffffffL) * PRIME64_1
      h64 = rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3
      off += 4
    else ()

    while off < end do
      h64 ^= (readByte(buf, off) & 0xff) * PRIME64_5
      h64 = rotateLeft(h64, 11) * PRIME64_1
      off += 1

    h64 ^= (h64 >>> 33)
    h64 *= PRIME64_2
    h64 ^= (h64 >>> 29)
    h64 *= PRIME64_3
    h64 ^= (h64 >>> 32)

    h64
  end hash

  override def hash(buffer: ByteBuffer, offset: Int, len: Int, seed: Long): Long =
    if buffer.hasArray() then hash(buffer.array(), offset + buffer.arrayOffset(), len, seed)
    else
      var off = offset
      ByteBufferUtils.checkRange(buffer, off, len)
      val buf = ByteBufferUtils.inLittleEndianOrder(buffer)

      val end: Int = off + len
      var h64: Long = 0L

      if len >= 32 then
        val limit: Int = end - 32
        var v1: Long = seed + PRIME64_1 + PRIME64_2
        var v2: Long = seed + PRIME64_2
        var v3: Long = seed + 0
        var v4: Long = seed - PRIME64_1
        while
          v1 = v1 + readLongLE(buf, off) * PRIME64_2
          v1 = rotateLeft(v1, 31)
          v1 = v1 * PRIME64_1
          off = off + 8

          v2 += readLongLE(buf, off) * PRIME64_2
          v2 = rotateLeft(v2, 31)
          v2 *= PRIME64_1
          off = off + 8

          v3 += readLongLE(buf, off) * PRIME64_2
          v3 = rotateLeft(v3, 31)
          v3 *= PRIME64_1
          off = off + 8

          v4 += readLongLE(buf, off) * PRIME64_2
          v4 = rotateLeft(v4, 31)
          v4 *= PRIME64_1
          off = off + 8

          off <= limit
        do ()

        h64 = rotateLeft(v1, 1) + rotateLeft(v2, 7) + rotateLeft(v3, 12) + rotateLeft(v4, 18)

        v1 *= PRIME64_2
        v1 = rotateLeft(v1, 31)
        v1 *= PRIME64_1
        h64 ^= v1
        h64 = h64 * PRIME64_1 + PRIME64_4

        v2 *= PRIME64_2
        v2 = rotateLeft(v2, 31)
        v2 *= PRIME64_1
        h64 ^= v2
        h64 = h64 * PRIME64_1 + PRIME64_4

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

      h64 += len

      while off <= end - 8 do
        var k1: Long = readLongLE(buf, off)
        k1 *= PRIME64_2
        k1 = rotateLeft(k1, 31)
        k1 *= PRIME64_1
        h64 ^= k1
        h64 = rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4
        off = off + 8

      if off <= end - 4 then
        h64 ^= (readIntLE(buf, off) & 0xffffffffL) * PRIME64_1
        h64 = rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3
        off = off + 4
      else ()

      while off < end do
        h64 ^= (readByte(buf, off) & 0xff) * PRIME64_5
        h64 = rotateLeft(h64, 11) * PRIME64_1
        off += 1

      h64 ^= h64 >>> 33
      h64 *= PRIME64_2
      h64 ^= h64 >>> 29
      h64 *= PRIME64_3
      h64 ^= h64 >>> 32

      h64
    end if
  end hash

end XXHash64VarHandle
