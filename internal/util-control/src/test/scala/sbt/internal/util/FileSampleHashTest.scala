/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import verify.BasicTestSuite
import sbt.io.IO
import sbt.io.syntax.*

object FileSampleHashTest extends BasicTestSuite:
  val emptyHash = -1205034819632174695L
  val testHash = 2563739794714397383L

  test("Hash empty file"):
    val hash64 = Hashing.samplingFileHashXXHash64(0)
    IO.withTemporaryDirectory: dir =>
      val temp = dir / "test.txt"
      IO.touch(temp)
      val h = hash64.hash(temp)
      assert(h == emptyHash)

  test("Hash small file"):
    val hash64 = Hashing.samplingFileHashXXHash64(0)
    IO.withTemporaryDirectory: dir =>
      val temp = dir / "test.txt"
      IO.write(temp, "test")
      val h = hash64.hash(temp)
      assert(h == testHash)

  test("Hash medium file (1MB)"):
    val hash64 = Hashing.samplingFileHashXXHash64(0)
    IO.withTemporaryDirectory: dir =>
      val temp = dir / "test.txt"
      val buf: Array[Byte] = Array.fill[Byte](1024)(0.toByte)
      for i <- 0 until 1024 do IO.append(temp, buf)
      val h = hash64.hash(temp)
      assert(h == -5176567862428962592L)
end FileSampleHashTest
