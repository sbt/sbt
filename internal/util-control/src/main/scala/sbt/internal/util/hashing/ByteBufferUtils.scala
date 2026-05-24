/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.nio.{ ByteBuffer, ByteOrder }

object ByteBufferUtils:
  def checkRange(buf: ByteBuffer, off: Int): Unit =
    if off < 0 || off >= buf.capacity() then throw new ArrayIndexOutOfBoundsException(off)
    else ()

  def checkRange(buf: ByteBuffer, off: Int, len: Int): Unit =
    SafeUtils.checkLength(len)
    if len > 0 then
      checkRange(buf, off)
      checkRange(buf, off + len - 1)
    else ()

  def inLittleEndianOrder(buf: ByteBuffer): ByteBuffer =
    if buf.order() == ByteOrder.LITTLE_ENDIAN then buf
    else buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
end ByteBufferUtils
