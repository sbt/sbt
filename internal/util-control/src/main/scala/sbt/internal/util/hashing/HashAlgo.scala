/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

// import java.nio.ByteBuffer

import scala.annotation.nowarn

/**
 * Hash algorithm interface
 */
@nowarn
trait HashAlgo[A1: Access]:

  /**
   * Computes the 64-bits hash of buf[off:off+len] using the seed.
   *
   * @param buf the input data
   * @param off the start offset in buf
   * @param len the number of bytes to hash
   * @return the hash value
   */
  def hash(buf: A1, off: Int, len: Int): Long

  override def toString(): String =
    getClass().getSimpleName()

end HashAlgo
