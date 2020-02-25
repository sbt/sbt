/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.{ Closeable, InputStream }
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
