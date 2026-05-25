/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

object WyHashTest extends AbstractHashTest:
  override val hash64: HashAlgo = Hashing.wyhash64
  override def newStreaming(seed: Int): StreamingHashAlgo =
    Hashing.newStreamingWyHash64(seed)
  override val emptyHash = 290873116282709081L
  override val zeroHash = -295637713410278011L
end WyHashTest
