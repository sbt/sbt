/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.io.Closeable

/**
 * Streaming interface for hashing.
 * The implementation is based on lz4-java.
 * Copyright 2020 Linnaea Von Lavia and the lz4-java contributors.
 * Licensed under the Apache License.
 *
 * Instances of this class are **not** thread-safe.
 */
abstract class StreamingHashAlgo(val seed: Long) extends Closeable:
  /**
   * Returns the value of the checksum.
   *
   * @return the checksum
   */
  def getValue: Long

  /**
   * Updates the value of the hash with buf[off:off+len].
   *
   * @param buf the input data
   * @param off the start offset in buf
   * @param len the number of bytes to hash
   */
  def update(buf: Array[Byte], off: Int, len: Int): Unit

  /**
   * Resets this instance to the state it had right after instantiation. The
   * seed remains unchanged.
   */
  def reset(): Unit

  /**
   * Releases any system resources associated with this instance.
   * It is not mandatory to call this method after using this instance
   * because the system resources are released anyway when this instance
   * is reclaimed by GC.
   */
  override def close(): Unit = ()

  override def toString: String =
    getClass().getSimpleName() + "(seed=" + seed + ")"
end StreamingHashAlgo
