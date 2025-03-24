/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.shaded.scalajson.ast.unsafe.*
import sjsonnew.*, support.scalajson.unsafe.*
import org.scalatest.flatspec.AnyFlatSpec
import sbt.io.IO

class FileInfoSpec extends AnyFlatSpec {
  val file = new java.io.File(".").getAbsoluteFile
  val fileInfo: ModifiedFileInfo = FileModified(file, IO.getModifiedTimeOrZero(file))
  val filesInfo = FilesInfo(Set(fileInfo))

  it should "round trip" in assertRoundTrip(filesInfo)

  def assertRoundTrip[A: JsonWriter: JsonReader](x: A) = {
    val jsonString: String = toJsonString(x)
    val jValue: JValue = Parser.parseUnsafe(jsonString)
    val y: A = Converter.fromJson[A](jValue).get
    assert(x === y)
  }

  def assertJsonString[A: JsonWriter](x: A, s: String) = assert(toJsonString(x) === s)

  def toJsonString[A: JsonWriter](x: A): String = CompactPrinter(Converter.toJson(x).get)
}
