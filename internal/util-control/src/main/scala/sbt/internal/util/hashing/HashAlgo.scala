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

/**
 * Hash algorithm interface
 */
trait HashAlgo:

  /**
   * Computes the 64-bits hash of buf[off:off+len] using the seed.
   *
   * @param buf the input data
   * @param off the start offset in buf
   * @param len the number of bytes to hash
   * @param seed the seed to use
   * @return the hash value
   */
  def hash(buf: Array[Byte], off: Int, len: Int, seed: Long): Long

  /**
   * Computes the hash of the given slice of the ByteBuffer.
   * ByteBuffer#position() position and ByteBuffer#limit() limit
   * are not modified.
   *
   * @param buf the input data
   * @param off the start offset in buf
   * @param len the number of bytes to hash
   * @param seed the seed to use
   * @return the hash value
   */
  def hash(buf: ByteBuffer, off: Int, len: Int, seed: Long): Long

  /**
   * Computes the hash of the given ByteBuffer. The
   * ByteBuffer#position() position is moved in order to reflect bytes
   * which have been read.
   *
   * @param buf the input data
   * @param seed the seed to use
   * @return the hash value
   */
  def hash(buf: ByteBuffer, seed: Long): Long =
    val r = hash(buf, buf.position(), buf.remaining(), seed)
    buf.position(buf.limit())
    r

  override def toString(): String =
    getClass().getSimpleName()

end HashAlgo
