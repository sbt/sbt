/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.lang.invoke.{ MethodHandles, VarHandle }
import java.nio.{ ByteBuffer, ByteOrder }

object VarHandleUtils:
  private def getArrayClass(c: Class[?]): Class[?] =
    java.lang.reflect.Array.newInstance(c, 0).getClass
  private val LONG_HANDLE: VarHandle =
    MethodHandles.byteArrayViewVarHandle(getArrayClass(classOf[Long]), ByteOrder.LITTLE_ENDIAN)
  private val INT_HANDLE: VarHandle =
    MethodHandles.byteArrayViewVarHandle(getArrayClass(classOf[Int]), ByteOrder.LITTLE_ENDIAN)
  private val BB_LONG_HANDLE: VarHandle =
    MethodHandles.byteBufferViewVarHandle(getArrayClass(classOf[Long]), ByteOrder.LITTLE_ENDIAN)
  private val BB_INT_HANDLE: VarHandle =
    MethodHandles.byteBufferViewVarHandle(getArrayClass(classOf[Int]), ByteOrder.LITTLE_ENDIAN)

  inline def readByte(buf: Array[Byte], off: Int): Byte =
    buf(off)
  inline def readIntLE(buf: Array[Byte], off: Int): Int =
    INT_HANDLE.get(buf, off).asInstanceOf[Int]
  inline def readLongLE(buf: Array[Byte], off: Int): Long =
    LONG_HANDLE.get(buf, off).asInstanceOf[Long]
  inline def readByte(buf: ByteBuffer, i: Int): Byte =
    buf.get(i)
  inline def readIntLE(buf: ByteBuffer, i: Int): Int =
    assert(buf.order() == ByteOrder.LITTLE_ENDIAN)
    BB_INT_HANDLE.get(buf, i).asInstanceOf[Int]
  inline def readLongLE(buf: ByteBuffer, i: Int): Long =
    assert(buf.order() == ByteOrder.LITTLE_ENDIAN)
    BB_LONG_HANDLE.get(buf, i).asInstanceOf[Long]
end VarHandleUtils
