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
import scala.collection.mutable
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
    var bytes = new mutable.ArrayBuffer[Byte]
    def getLine(): String = {
      val line = new String(bytes.toArray, "UTF-8")
      bytes = new mutable.ArrayBuffer[Byte]
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
                      else bytes += c.toByte
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
          bytes += c.toByte

      }
    } while (content.isEmpty && running.get)
    content
  }

}
