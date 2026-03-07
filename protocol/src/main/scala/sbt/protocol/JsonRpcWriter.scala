/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol

import java.io.OutputStream

object JsonRpcWriter {

  def write(out: OutputStream, message: String): Unit = {
    val bytes = message.getBytes("UTF-8")
    writeLine(out, s"Content-Length: ${bytes.length + 2}".getBytes("UTF-8"))
    writeLine(out, Array.emptyByteArray)
    writeLine(out, bytes)
  }

  def writeLine(out: OutputStream, bytes: Array[Byte]): Unit = {
    if (bytes.nonEmpty) out.write(bytes)
    out.write('\r'.toByte.toInt)
    out.write('\n'.toByte.toInt)
    out.flush()
  }
}
