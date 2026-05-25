/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.io.{ File, RandomAccessFile }
import java.nio.ByteBuffer
import java.nio.file.{ Path as NioPath }
import scala.util.Using

object FileSampleHash:
  final val defaultSampleBytes = 16 * 1024
  final val defaultThresoldBytes = 128L * 1024L

  def apply(underlying: StreamingHashAlgo): FileSampleHash =
    new FileSampleHash(defaultSampleBytes, defaultThresoldBytes, underlying)
end FileSampleHash

/**
 * Based on Imohash https://github.com/kalafut/imohash/blob/master/algorithm.md
 */
class FileSampleHash(sampleBytes: Int, thresholdBytes: Long, underlying: StreamingHashAlgo)
    extends FileHash:
  require(sampleBytes >= 0)

  val buffer: Array[Byte] = new Array[Byte](4096)

  override def hash(file: NioPath): Long =
    hash(file.toFile())

  override def hash(file: File): Long =
    Using.resource(new RandomAccessFile(file, "r")): raf =>
      hash(raf, raf.length())

  private def hash(input: RandomAccessFile, fileLength: Long): Long =
    underlying.reset()
    if fileLength < thresholdBytes || sampleBytes < 1 then hashBytes(input, fileLength)
    else
      hashBytes(input, sampleBytes)
      // skip to halfway point
      input.seek(fileLength / 2)
      hashBytes(input, sampleBytes)
      input.seek(fileLength - sampleBytes)
      hashBytes(input, sampleBytes)

    // write file size
    if fileLength > 0 then
      val sizeBuf = ByteBuffer.allocate(java.lang.Long.BYTES)
      sizeBuf.putLong(fileLength)
      underlying.update(sizeBuf.array(), 0, sizeBuf.array().size)

    underlying.getValue
  end hash

  private def hashBytes(input: RandomAccessFile, toHash: Long): Unit =
    var remaining: Long = toHash
    var pos = 0
    while remaining > 0 do
      val toread = math.min(buffer.size - pos, remaining).toInt
      val bytesRead = input.read(buffer, pos, toread)
      if bytesRead < 0 then sys.error("unexpected EOF")
      pos += bytesRead
      remaining -= bytesRead
      if pos >= buffer.length then
        underlying.update(buffer, 0, buffer.length)
        pos = 0
    if pos > 0 then underlying.update(buffer, 0, pos)
  end hashBytes
end FileSampleHash
