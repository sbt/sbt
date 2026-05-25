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
import WyHashConstants.*
import VarHandleUtils.*

object WyHash64VarHandle:
  private[hashing] val INSTANCE = new WyHash64VarHandle()

  private[hashing] inline def initSeed(seed: Long): Long =
    seed ^ mix(seed ^ PRIME64_0, PRIME64_1)

  private[hashing] def mix(a: Long, b: Long): Long =
    val low = a * b
    val high = unsignedMultiplyHigh(a, b)
    low ^ high

  private[hashing] inline def unsignedMultiplyHigh(a: Long, b: Long): Long =
    Math.multiplyHigh(a, b) + ((a >> 63) & b) + ((b >> 63) & a)

  private[hashing] inline def wyr3(buf: Array[Byte], off: Int, k: Int): Long =
    ((buf(off) & 0xffL) << 16)
      | ((buf(off + (k >> 1)) & 0xffL) << 8)
      | (buf(off + k - 1) & 0xffL)

  private[hashing] inline def wyr3(buf: ByteBuffer, off: Int, k: Int): Long =
    ((buf.get(off) & 0xffL) << 16)
      | ((buf.get(off + (k >> 1)) & 0xffL) << 8)
      | (buf.get(off + k - 1) & 0xffL)

  private[hashing] inline def finishHash(a: Long, b: Long, seed: Long, len: Long): Long =
    val _a = a ^ PRIME64_1
    val _b = b ^ seed
    val low = _a * _b
    val high = unsignedMultiplyHigh(_a, _b)
    mix(low ^ PRIME64_0 ^ len, high ^ PRIME64_1)

end WyHash64VarHandle

/**
 * Wyhash matching Zig 0.15 std.hash.Wyhash.
 */
class WyHash64VarHandle extends HashAlgo:
  import WyHash64VarHandle.*

  override def hash(buf: Array[Byte], offset: Int, len: Int, seed: Long): Long =
    SafeUtils.checkRange(buf, offset, len)

    var off = offset
    var s: Long = initSeed(seed)
    val secret1 = PRIME64_1
    val secret2 = PRIME64_2
    val secret3 = PRIME64_3
    var a: Long = 0L
    var b: Long = 0L

    if len <= 16 then
      if len >= 4 then
        a = (readIntLE(buf, off).toLong << 32)
          | (readIntLE(buf, off + ((len >> 3) << 2)) & 0xffffffffL)
        b = (readIntLE(buf, off + len - 4).toLong << 32)
          | (readIntLE(buf, off + len - 4 - ((len >> 3) << 2)) & 0xffffffffL)
      else if len > 0 then
        a = wyr3(buf, off, len)
        b = 0
      else
        a = 0
        b = 0
    else
      var i = len
      var p = off
      var see0 = s
      var see1 = s
      var see2 = s

      while i > 48 do
        see0 = mix(readLongLE(buf, p) ^ secret1, readLongLE(buf, p + 8) ^ see0)
        see1 = mix(readLongLE(buf, p + 16) ^ secret2, readLongLE(buf, p + 24) ^ see1)
        see2 = mix(readLongLE(buf, p + 32) ^ secret3, readLongLE(buf, p + 40) ^ see2)
        p += 48
        i -= 48
      end while

      see0 ^= see1 ^ see2
      while i > 16 do
        see0 = mix(readLongLE(buf, p) ^ secret1, readLongLE(buf, p + 8) ^ see0)
        i -= 16
        p += 16
      end while

      a = readLongLE(buf, off + len - 16)
      b = readLongLE(buf, off + len - 8)
      s = see0
    end if
    finishHash(a, b, s, len)
  end hash

  override def hash(buffer: ByteBuffer, offset: Int, len: Int, seed: Long): Long =
    if buffer.hasArray() then hash(buffer.array(), offset + buffer.arrayOffset(), len, seed)
    else
      var off = offset
      ByteBufferUtils.checkRange(buffer, off, len)
      val buf = ByteBufferUtils.inLittleEndianOrder(buffer)
      var s: Long = initSeed(seed)
      val secret1 = PRIME64_1
      val secret2 = PRIME64_2
      val secret3 = PRIME64_3
      var a: Long = 0L
      var b: Long = 0L

      if len <= 16 then
        if len >= 4 then
          a = (readIntLE(buf, off).toLong << 32)
            | (readIntLE(buf, off + ((len >> 3) << 2)) & 0xffffffffL)
          b = (readIntLE(buf, off + len - 4).toLong << 32)
            | (readIntLE(buf, off + len - 4 - ((len >> 3) << 2)) & 0xffffffffL)
        else if len > 0 then
          a = wyr3(buf, off, len)
          b = 0
        else
          a = 0
          b = 0
      else
        var i = len
        var p = off
        var see0 = s
        var see1 = s
        var see2 = s

        while i > 48 do
          see0 = mix(readLongLE(buf, p) ^ secret1, readLongLE(buf, p + 8) ^ see0)
          see1 = mix(readLongLE(buf, p + 16) ^ secret2, readLongLE(buf, p + 24) ^ see1)
          see2 = mix(readLongLE(buf, p + 32) ^ secret3, readLongLE(buf, p + 40) ^ see2)
          p += 48
          i -= 48
        end while

        see0 ^= see1 ^ see2
        while i > 16 do
          see0 = mix(readLongLE(buf, p) ^ secret1, readLongLE(buf, p + 8) ^ see0)
          i -= 16
          p += 16
        end while

        a = readLongLE(buf, off + len - 16)
        b = readLongLE(buf, off + len - 8)
        s = see0
      end if
      finishHash(a, b, s, len)
    end if
  end hash

end WyHash64VarHandle
