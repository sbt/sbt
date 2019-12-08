/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.io.{ Closeable, OutputStream }
import sjsonnew.{ IsoString, JsonWriter, SupportConverter }
import sbt.io.Using

trait Output extends Closeable {
  def write[T: JsonWriter](value: T): Unit
}

class PlainOutput[J: IsoString](output: OutputStream, converter: SupportConverter[J])
    extends Output {
  val isoFormat: IsoString[J] = implicitly

  def write[T: JsonWriter](value: T) = {
    val js = converter.toJson(value).get
    val asString = isoFormat.to(js)
    Using.bufferedOutputStream(output) { writer =>
      val out = new java.io.PrintWriter(writer)
      out.print(asString)
      out.flush()
    }
  }

  def close() = output.close()
}
