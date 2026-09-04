/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.{ BufferedInputStream, Closeable, File, InputStream }
import java.nio.ByteBuffer
import java.nio.file.Files

import scala.util.control.NonFatal
import sjsonnew.{ IsoString, JsonReader, SupportConverter }
import sbt.io.{ IO, Using }
import sbt.internal.util.EmptyCacheError

trait Input extends Closeable {
  def read[T: JsonReader](): T
  def read[T: JsonReader](default: => T): T =
    try read[T]()
    catch { case NonFatal(_) => default }
}

class PlainInput[J: IsoString](input: InputStream, converter: SupportConverter[J]) extends Input {
  val isoFormat: IsoString[J] = implicitly

  private def readFully(): String = {
    Using.streamReader((input, IO.utf8)) { reader =>
      val builder = new StringBuilder()
      val bufferSize = 1024
      val buffer = new Array[Char](bufferSize)
      var read = 0
      while ({ read = reader.read(buffer, 0, bufferSize); read != -1 }) {
        builder.appendAll(buffer, 0, read)
      }

      builder.toString()
    }
  }

  def read[T: JsonReader](): T = {
    val str = readFully()
    if (str == "") throw new EmptyCacheError()
    else converter.fromJson(isoFormat.from(str)).get
  }

  def close() = input.close()
}

class FileInput(file: File) extends Input {

  override def read[T: JsonReader](): T = {
    sjsonnew.support.scalajson.unsafe.Converter
      .fromJson(sjsonnew.support.scalajson.unsafe.Parser.parseFromFile(file).get)
      .get
  }

  def close() = ()
}

/** Sniffs the framing rather than trusting the name, so a cache written uncompressed still loads. */
private[sbt] class GzipFileInput(file: File) extends Input {

  override def read[T: JsonReader](): T = {
    val json = Using.fileInputStream(file) { raw =>
      val buffered = new BufferedInputStream(raw)
      buffered.mark(2)
      val gzipped = buffered.read() == 0x1f && buffered.read() == 0x8b
      buffered.reset()
      val bytes =
        if (gzipped) Using.gzipInputStream(buffered)(IO.readBytes)
        else IO.readBytes(buffered)
      if (bytes.isEmpty) throw new EmptyCacheError()
      sjsonnew.support.scalajson.unsafe.Parser.parseFromByteArray(bytes).get
    }
    sjsonnew.support.scalajson.unsafe.Converter.fromJson(json).get
  }

  def close() = ()
}

private[sbt] object GzipFileInput {

  /**
   * What `file` holds once inflated: gzip records it in the last four bytes of the member. Anything
   * that is not a whole gzip member weighs what it occupies on disk instead.
   */
  def uncompressedSize(file: File): Long = {
    val channel = Files.newByteChannel(file.toPath)
    try {
      val size = channel.size
      // Shorter than an empty gzip member, so there is no trailer to read.
      if (size < 18) size
      else {
        def readAt(position: Long, n: Int): Option[ByteBuffer] = {
          val buffer = ByteBuffer.allocate(n)
          channel.position(position)
          while (buffer.hasRemaining && channel.read(buffer) > 0) ()
          // A short read leaves zeros behind, which would pass for a valid ISIZE.
          if (buffer.hasRemaining) None else Some(buffer)
        }
        val isize =
          for
            magic <- readAt(0, 2)
            if (magic.get(0) & 0xff) == 0x1f && (magic.get(1) & 0xff) == 0x8b
            trailer <- readAt(size - 4, 4)
          yield (0 until 4).foldLeft(0L)((acc, i) => acc | ((trailer.get(i) & 0xffL) << (8 * i)))
        // ISIZE is the payload length modulo 2^32, so it is only a floor above that.
        isize.filter(_ > 0).getOrElse(size)
      }
    } finally channel.close()
  }
}
