/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.{ BufferedInputStream, Closeable, File, InputStream }

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
