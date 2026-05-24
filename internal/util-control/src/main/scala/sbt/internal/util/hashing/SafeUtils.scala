/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

object SafeUtils:
  def checkRange(buf: Array[Byte], off: Int): Unit =
    if off < 0 || off >= buf.length then throw new ArrayIndexOutOfBoundsException(off)
    else ()

  def checkRange(buf: Array[Byte], off: Int, len: Int): Unit =
    checkLength(len)
    if len > 0 then
      checkRange(buf, off)
      checkRange(buf, off + len - 1)
    else ()

  def checkLength(len: Int): Unit =
    if len < 0 then throw new IllegalArgumentException("lengths must be >= 0")
    else ()
end SafeUtils
