/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.lang.Long.rotateRight
import java.nio.ByteBuffer
import FarmHashConstants.*

object FarmHash64:
  private inline def shiftMix(x: Long): Long =
    x ^ (x >>> 47)

  private inline def hashLen16(u: Long, v: Long): Long =
    hashLen16(u, v, K_MUL)

  private inline def hashLen16(u: Long, v: Long, m: Long): Long =
    val a = shiftMix((u ^ v) * m)
    shiftMix((v ^ a) * m) * m

  private inline def mul(len: Long): Long =
    K2 + (len << 1)

  private def hash1To3Bytes(len: Int, firstByte: Int, midOrLastByte: Int, lastByte: Int): Long =
    val y = firstByte + (midOrLastByte << 8)
    val z = len + (lastByte << 2)
    shiftMix((y.toLong * K2) ^ (z.toLong * K0)) * K2

  private def hash4To7Bytes(len: Long, first4Bytes: Long, last4Bytes: Long): Long =
    val m = mul(len)
    hashLen16(len + (first4Bytes << 3), last4Bytes, m)

  private def hash8To16Bytes(len: Long, first8Bytes: Long, last8Bytes: Long): Long =
    val m = mul(len)
    val a = first8Bytes + K2
    val c = rotateRight(last8Bytes, 37) * m + a
    val d = (rotateRight(a, 25) + last8Bytes) * m
    hashLen16(c, d, m)

  private def hashLen0To16[A1](in: A1, offset: Long, len: Long)(access: Access[A1]): Long =
    val off = offset.toInt
    if len >= 8L then
      val a = access.readLongLE(in, off)
      val b = access.readLongLE(in, (off + len - 8L).toInt)
      hash8To16Bytes(len, a, b)
    else if len >= 4L then
      val a = access.readIntLE(in, off) & 0xffffffffL
      val b = access.readIntLE(in, (off + len - 4L).toInt) & 0xffffffffL
      hash4To7Bytes(len, a, b)
    else if len > 0L then
      val a = access.readByte(in, off)
      val b = access.readByte(in, (off + (len >> 1)).toInt)
      val c = access.readByte(in, (off + len - 1).toInt)
      hash1To3Bytes(len.toInt, a, b, c)
    else K2

  private def hashLen17To32[A1](in: A1, offset: Long, len: Long)(access: Access[A1]): Long =
    val off = offset.toInt
    val m = mul(len)
    val a = access.readLongLE(in, off) * K1
    val b = access.readLongLE(in, off + 8)
    val c = access.readLongLE(in, (off + len - 8L).toInt) * m
    val d = access.readLongLE(in, (off + len - 16L).toInt) * K2
    hashLen16(rotateRight(a + b, 43) + rotateRight(c, 30) + d, a + rotateRight(b + K2, 18) + c, m)

  private def naHashLen33To64[A1](in: A1, offset: Long, len: Long)(access: Access[A1]): Long =
    val off = offset.toInt
    val m = mul(len)
    val a = access.readLongLE(in, off) * K2
    val b = access.readLongLE(in, off + 8)
    val c = access.readLongLE(in, (off + len - 8).toInt) * m
    val d = access.readLongLE(in, (off + len - 16).toInt) * K2
    val y = rotateRight(a + b, 43) + rotateRight(c, 30) + d
    val z = hashLen16(y, a + rotateRight(b + K2, 18) + c, m)
    val e = access.readLongLE(in, off + 16) * m
    val f = access.readLongLE(in, off + 24)
    val g = (y + access.readLongLE(in, (off + len - 32).toInt)) * m
    val h = (z + access.readLongLE(in, (off + len - 24).toInt)) * m
    hashLen16(rotateRight(e + f, 43) + rotateRight(g, 30) + h, e + rotateRight(f + a, 18) + g, m)

  def naHash64[A1](in: A1, offset: Long, len: Long)(access: Access[A1]): Long =
    val seed: Long = 81L
    if len <= 32 then
      if len <= 16 then hashLen0To16(in, offset, len)(access)
      else hashLen17To32(in, offset, len)(access)
    else if len <= 64 then naHashLen33To64(in, offset, len)(access)
    else
      var off = offset.toInt
      // For strings over 64 bytes we loop.  Internal state consists of
      // 56 bytes: v, w, x, y, and z.
      var x: Long = seed
      // == seed * k1 + 113 This overflows uint64 and is a compile error,
      // so we expand the constant by hand
      var y: Long = seed * K1 + 113
      var z: Long = shiftMix(y * K2 + 113) * K2
      var v1: Long = 0L
      var v2: Long = 0L
      var w1: Long = 0L
      var w2: Long = 0L
      x = x * K2 + access.readLongLE(in, off)

      // Set end so that after the loop we have 1 to 64 bytes left to process.
      val fin = off + ((len - 1) >> 6) * 64
      val last64 = fin + ((len - 1) & 63) - 63

      while
        x = rotateRight(x + y + v1 + access.readLongLE(in, (off + 8).toInt), 37) * K1
        y = rotateRight(y + v2 + access.readLongLE(in, (off + 48).toInt), 42) * K1
        x ^= w2
        y += v1 + access.readLongLE(in, off + 40)
        z = rotateRight(z + w1, 33) * K1
        var a: Long = v2 * K1
        var b: Long = x + w1
        val z1 = access.readLongLE(in, off + 24)
        a += access.readLongLE(in, off)
        b = rotateRight(b + a + z1, 21)
        val c = a
        a += access.readLongLE(in, off + 8)
        a += access.readLongLE(in, off + 16)
        b += rotateRight(a, 44)
        v1 = a + z1
        v2 = b + c
        var a1 = z + w2
        var b1 = y + access.readLongLE(in, off + 16)
        var z2 = access.readLongLE(in, off + 32 + 24)
        a1 += access.readLongLE(in, off + 32)
        b1 = rotateRight(b1 + a1 + z2, 21)
        val c1 = a1
        a1 += access.readLongLE(in, off + 32 + 8)
        a1 += access.readLongLE(in, off + 32 + 16)
        b1 += rotateRight(a1, 44)
        w1 = a1 + z2
        w2 = b1 + c1
        val t = z
        z = x
        x = t
        off += 64
        off != fin
      do ()
      end while

      off = last64.toInt

      val m = K1 + ((z & 0xff) << 1)

      // Make s point to the last 64 bytes of input.
      w1 += (len - 1) & 63
      v1 += w1
      w1 += v1
      x = rotateRight(x + y + v1 + access.readLongLE(in, off + 8), 37) * m
      y = rotateRight(y + v2 + access.readLongLE(in, off + 48), 42) * m
      x ^= w2 * 9
      y += v1 * 9 + access.readLongLE(in, off + 40)
      z = rotateRight(z + w1, 33) * m
      var a: Long = v2 * m
      var b: Long = x + w1
      val z1 = access.readLongLE(in, off + 24)
      a += access.readLongLE(in, off)
      b = rotateRight(b + a + z1, 21)
      val c = a
      a += access.readLongLE(in, off + 8)
      a += access.readLongLE(in, off + 16)
      b += rotateRight(a, 44)
      v1 = a + z1
      v2 = b + c
      var a1: Long = z + w2
      var b1: Long = y + access.readLongLE(in, off + 16)
      val z2 = access.readLongLE(in, off + 32 + 24)
      a1 += access.readLongLE(in, off + 32)
      b1 = rotateRight(b1 + a1 + z2, 21)
      val c1 = a1
      a1 += access.readLongLE(in, off + 32 + 8)
      a1 += access.readLongLE(in, off + 32 + 16)
      b1 += rotateRight(a1, 44)
      w1 = a1 + z2
      w2 = b1 + c1
      val t = z
      z = x
      x = t
      hashLen16(hashLen16(v1, w1, m) + shiftMix(y) * K0 + z, hashLen16(v2, w2, m) + x, m)
    end if
  end naHash64
end FarmHash64

object FarmNaSeedlessHash64:

  private lazy val arrayInstance: FarmNaSeedlessHash64[Array[Byte]] =
    new FarmNaSeedlessHash64()
  private lazy val byteBufferInstance: FarmNaSeedlessHash64[ByteBuffer] =
    new FarmNaSeedlessHash64()

  def byteArray: FarmNaSeedlessHash64[Array[Byte]] =
    arrayInstance

  def byteBuffer: FarmNaSeedlessHash64[ByteBuffer] =
    byteBufferInstance
end FarmNaSeedlessHash64

class FarmNaSeedlessHash64[A1: Access] extends HashAlgo:
  import FarmHash64.*
  private val access: Access[A1] = summon[Access[A1]]

  override def hash(buf: A1, offset: Int, len: Int): Long =
    val hash = naHash64(buf, offset, len)(access)
    hash
end FarmNaSeedlessHash64
