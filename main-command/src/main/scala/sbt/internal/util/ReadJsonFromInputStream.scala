/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.InputStream
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Try

private[sbt] object ReadJsonFromInputStream {
  def apply(
      inputStream: InputStream,
      running: AtomicBoolean,
      onHeader: Option[String => Unit]
  ): Seq[Byte] = {
    val newline = '\n'.toInt
    val carriageReturn = '\r'.toInt
    val contentLength = "Content-Length: "
    var index = 0
    /*
     * This is the buffer into which we copy headers. The value of 128 bytes is
     * somewhat arbitrary but chosen to ensure that there is enough space
     * for any reasonable header. Any header exceeding 128 bytes will
     * be truncated. The only header we care about at the moment is
     * content-length, so this should be fine. If we ever start doing anything
     * with headers, we may need to adjust this buffer size.
     */
    val headerBuffer = new Array[Byte](128)
    def getLine(): String = {
      val line = new String(headerBuffer, 0, index, "UTF-8")
      index = 0
      onHeader.foreach(oh => oh(line))
      line
    }
    var content: Seq[Byte] = Seq.empty[Byte]
    var consecutiveLineEndings = 0
    var onCarriageReturn = false
    do {
      val byte = inputStream.read
      byte match {
        case `newline` =>
          val line = getLine()
          if (onCarriageReturn) consecutiveLineEndings += 1
          onCarriageReturn = false
          if (line.startsWith(contentLength)) {
            Try(line.drop(contentLength.length).toInt) foreach { len =>
              def drainHeaders(): Unit =
                do {
                  inputStream.read match {
                    case `newline` if onCarriageReturn =>
                      getLine()
                      onCarriageReturn = false
                      consecutiveLineEndings += 1
                    case `carriageReturn` => onCarriageReturn = true
                    case c =>
                      if (c == newline) getLine()
                      else {
                        if (index < headerBuffer.length) headerBuffer(index) = c.toByte
                        index += 1
                      }
                      onCarriageReturn = false
                      consecutiveLineEndings = 0
                  }
                } while (consecutiveLineEndings < 2)
              drainHeaders()
              val buf = new Array[Byte](len)
              var offset = 0
              do {
                offset += inputStream.read(buf, offset, len - offset)
              } while (offset < len)
              content = buf.toSeq
            }
          } else if (line.startsWith("{")) {
            // Assume this is a json object with no headers
            content = line.getBytes.toSeq
          }
        case i if i < 0 =>
          running.set(false)
          throw new ClosedChannelException
        case `carriageReturn` => onCarriageReturn = true
        case c =>
          onCarriageReturn = false
          if (index < headerBuffer.length) headerBuffer(index) = c.toByte
          index += 1

      }
    } while (content.isEmpty && running.get)
    content
  }

}
