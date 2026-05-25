/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

object Hashing:
  def xxhash64: HashAlgo = XXHash64VarHandle.INSTANCE
  def wyhash64: HashAlgo = WyHash64VarHandle.INSTANCE

  def newStreamingXXHash64(seed: Long): StreamingHashAlgo =
    new StreamingXXHash64VarHandle(seed)
  def newStreamingWyHash64(seed: Long): StreamingHashAlgo =
    new StreamingWyHash64VarHandle(seed)
  def samplingFileHashXXHash64(seed: Long): FileHash =
    FileSampleHash(newStreamingXXHash64(seed))
  def samplingFileHashWyHash64(seed: Long): FileHash =
    FileSampleHash(newStreamingWyHash64(seed))
end Hashing
