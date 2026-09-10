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
import XXHashConstants.*

object XXHash64:
  private lazy val arrayInstance: XXHash64[Array[Byte]] =
    new XXHash64(0)
  private lazy val byteBufferInstance: XXHash64[ByteBuffer] =
    new XXHash64(0)

  def byteArray(seed: Long): XXHash64[Array[Byte]] =
    if seed == 0L then arrayInstance
    else new XXHash64(seed)

  def byteBuffer(seed: Long): XXHash64[ByteBuffer] =
    if seed == 0L then byteBufferInstance
    else new XXHash64(seed)
end XXHash64

/**
 * The implementation is based on lz4-java.
 * Copyright 2020 Linnaea Von Lavia and the lz4-java contributors.
 * Licensed under the Apache License.
 *
 * Instances of this class are **not** thread-safe.
 */
class XXHash64[A1: Access](seed: Long) extends HashAlgo[A1]:
  private val access: Access[A1] = summon[Access[A1]]

  override def hash(buf: A1, offset: Int, len: Int): Long =
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
        v1 += access.readLongLE(buf, off) * PRIME64_2
        v1 = rotateLeft(v1, 31)
        v1 *= PRIME64_1
        off += 8

        v2 += access.readLongLE(buf, off) * PRIME64_2
        v2 = rotateLeft(v2, 31)
        v2 *= PRIME64_1
        off += 8

        v3 += access.readLongLE(buf, off) * PRIME64_2
        v3 = rotateLeft(v3, 31)
        v3 *= PRIME64_1
        off += 8

        v4 += access.readLongLE(buf, off) * PRIME64_2
        v4 = rotateLeft(v4, 31)
        v4 = v4 * PRIME64_1
        off += 8
        off <= limit
      do ()
      end while

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
    end if

    h64 += len

    while off <= end - 8 do
      var k1: Long = access.readLongLE(buf, off)
      k1 *= PRIME64_2
      k1 = rotateLeft(k1, 31)
      k1 *= PRIME64_1
      h64 ^= k1
      h64 = rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4
      off += 8

    if off <= end - 4 then
      h64 ^= (access.readIntLE(buf, off) & 0xffffffffL) * PRIME64_1
      h64 = rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3
      off += 4
    else ()

    while off < end do
      h64 ^= (access.readByte(buf, off) & 0xff) * PRIME64_5
      h64 = rotateLeft(h64, 11) * PRIME64_1
      off += 1

    h64 ^= (h64 >>> 33)
    h64 *= PRIME64_2
    h64 ^= (h64 >>> 29)
    h64 *= PRIME64_3
    h64 ^= (h64 >>> 32)

    h64
  end hash
end XXHash64
