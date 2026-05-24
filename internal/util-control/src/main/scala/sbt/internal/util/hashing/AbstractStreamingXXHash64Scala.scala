/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import XXHashConstants.PRIME64_1
import XXHashConstants.PRIME64_2

abstract class AbstractStreamingXXHash64Scala(seed: Long) extends StreamingHashAlgo(seed):
  protected var memSize: Int = 0
  protected var v1: Long = 0
  protected var v2: Long = 0
  protected var v3: Long = 0
  protected var v4: Long = 0
  protected var totalLen: Long = 0
  protected val memory = new Array[Byte](32)
  reset()

  override def reset(): Unit =
    v1 = seed + PRIME64_1 + PRIME64_2
    v2 = seed + PRIME64_2
    v3 = seed + 0
    v4 = seed - PRIME64_1
    totalLen = 0
    memSize = 0

end AbstractStreamingXXHash64Scala
