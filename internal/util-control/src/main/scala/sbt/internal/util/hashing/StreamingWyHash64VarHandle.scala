/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import WyHash64VarHandle.*
import WyHashConstants.*
import VarHandleUtils.*

class StreamingWyHash64VarHandle(seed: Long) extends StreamingHashAlgo(seed):
  protected var a: Long = 0
  protected var b: Long = 0
  protected val state: Array[Long] = new Array[Long](3)
  protected var v0: Long = 0
  protected var v1: Long = 0
  protected var v2: Long = 0
  protected var totalLen: Long = 0L
  protected val memory = new Array[Byte](48)
  protected var memoryLen: Int = 0
  reset()

  override def reset(): Unit =
    val s: Long = initSeed(seed)
    this.v0 = s
    this.v1 = s
    this.v2 = s
    this.totalLen = 0
    this.memoryLen = 0

  def getValue: Long =
    var _a: Long = this.a
    var _b: Long = this.b
    var v0: Long = this.v0
    var v1: Long = this.v1
    var v2: Long = this.v2

    var input = this.memory
    var inputLen = this.memoryLen

    if this.totalLen <= 16 then
      if inputLen >= 4 then
        val end = inputLen - 4
        val quarter = (inputLen >> 3) << 2
        _a = (readIntLE(input, 0).toLong << 32)
          | (readIntLE(input, quarter) & 0xffffffffL)
        _b = (readIntLE(input, end) << 32).toLong
          | (readIntLE(input, end - quarter) & 0xffffffffL)
      else if inputLen > 0 then
        _a = ((input(0) & 0xffL) << 16) | ((input(inputLen >> 1) & 0xffL) << 8)
          | (input(inputLen - 1) & 0xffL)
        _b = 0
      else
        _a = 0
        _b = 0
      end if
    else
      var scratch: Array[Byte] = null
      if inputLen < 16 then
        val rem = 16 - inputLen
        scratch = new Array[Byte](16)
        System.arraycopy(memory, 48 - rem, scratch, 0, rem)
        System.arraycopy(memory, 0, scratch, rem, inputLen)
        input = scratch
        inputLen = 16

      if this.totalLen >= 48 then v0 ^= v1 ^ v2

      var i = 0
      while i + 16 < inputLen do
        v0 = mix(readLongLE(input, i) ^ PRIME64_1, readLongLE(input, i + 8) ^ v0)
        i += 16

      _a = readLongLE(input, inputLen - 16)
      _b = readLongLE(input, inputLen - 8)
    end if

    finishHash(_a, _b, v0, this.totalLen)
  end getValue

  def update(buf: Array[Byte], off: Int, len: Int): Unit =
    this.totalLen += len

    if len <= 48 - this.memoryLen then
      System.arraycopy(buf, off, this.memory, this.memoryLen, len)
      this.memoryLen += len
    else
      var i: Int = 0
      if this.memoryLen > 0 then
        i = 48 - this.memoryLen
        System.arraycopy(buf, off, this.memory, this.memoryLen, i)
        round(this.memory, 0)
        this.memoryLen = 0
      end if

      while i + 48 < len do
        round(buf, off + i)
        i += 48

      val remaining = len - i
      if remaining < 16 && i >= 48 then
        val rem = 16 - remaining
        System.arraycopy(buf, off + i - rem, this.memory, 48 - rem, rem)

      System.arraycopy(buf, off + i, this.memory, 0, remaining)
      this.memoryLen = remaining
    end if
  end update

  private def round(buf: Array[Byte], p: Int): Unit =
    this.v0 = mix(readLongLE(buf, p) ^ PRIME64_1, readLongLE(buf, p + 8) ^ this.v0)
    this.v1 = mix(readLongLE(buf, p + 16) ^ PRIME64_2, readLongLE(buf, p + 24) ^ this.v1)
    this.v2 = mix(readLongLE(buf, p + 32) ^ PRIME64_3, readLongLE(buf, p + 40) ^ this.v2)

end StreamingWyHash64VarHandle
